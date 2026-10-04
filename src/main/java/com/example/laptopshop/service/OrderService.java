package com.example.laptopshop.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Cart;
import com.example.laptopshop.domain.CartItem;
import com.example.laptopshop.domain.Voucher;
import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.OrderDetail;
import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.Payment;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.domain.Promotion;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.Client.CreateOrderRequest;
import com.example.laptopshop.dto.request.Client.ValidateVoucherRequest;
import com.example.laptopshop.dto.request.Order.OrderBulkStatusRequest;
import com.example.laptopshop.dto.response.Client.VoucherValidationResponse;
import com.example.laptopshop.dto.response.Client.FlashPriceView;
import com.example.laptopshop.dto.response.Client.OrderDetailResponse;
import com.example.laptopshop.dto.response.Client.OrderSummaryResponse;
import com.example.laptopshop.dto.response.Order.AdminOrderDetailResponse;
import com.example.laptopshop.dto.response.Order.AdminOrderResponse;
import com.example.laptopshop.dto.response.Order.OrderStatsResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.domain.UserVoucher;
import com.example.laptopshop.domain.UserVoucherStatus;
import com.example.laptopshop.repository.VoucherRepository;
import com.example.laptopshop.repository.OrderRepository;
import com.example.laptopshop.repository.ProductRepository;
import com.example.laptopshop.repository.PromotionRepository;
import com.example.laptopshop.repository.UserVoucherRepository;
import com.example.laptopshop.repository.UserRepository;
import com.example.laptopshop.service.CartService.CartPricing;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Đơn hàng của khách storefront.
 *
 * Nguyên tắc cốt lõi:
 * - Đơn LUÔN được dựng từ giỏ server, không nhận danh sách sản phẩm từ client
 *   → khách không sửa được giá/số lượng qua request.
 * - Snapshot tên/mã/ảnh/giá vào OrderDetail tại thời điểm mua; admin đổi giá
 *   sau đó không làm sai đơn cũ.
 * - Trừ tồn kho NGAY trong cùng transaction với việc tạo đơn, và kiểm tra lại
 *   tồn kho lần cuối trước khi trừ (chống race condition khi 2 khách mua cùng
 *   lúc sản phẩm cuối).
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OrderService {

    // Sinh mã đơn "LS" + 8 chữ số, ví dụ LS12345678. Mockup dùng "LS000456"
    // (6 số) nhưng 8 số giảm mạnh khả năng trùng khi số đơn tăng.
    static final String ORDER_CODE_PREFIX = "LS";
    static final int ORDER_CODE_DIGITS = 8;
    // Số lần thử lại tối đa khi mã đơn bị trùng (unique constraint).
    static final int ORDER_CODE_MAX_ATTEMPTS = 10;

    static final SecureRandom RANDOM = new SecureRandom();

    OrderRepository orderRepository;
    VoucherRepository voucherRepository;
    UserRepository userRepository;
    ProductRepository productRepository;
    UserVoucherRepository userVoucherRepository;
    CartService cartService;
    VoucherService voucherService;
    PaymentService paymentService;
    PromotionService promotionService;
    PromotionEngine promotionEngine;
    PromotionRepository promotionRepository;
    FlashSaleService flashSaleService;
    VoucherWalletService voucherWalletService;

    /**
     * Map dòng giỏ hàng sang đầu vào engine, kèm giá flash (D25). Cùng nguồn
     * giá với CartService để preview = chốt đơn (D14).
     *
     * @param flash giá flash theo productId, đã tính tại thời điểm chốt (D27)
     */
    private List<PromotionEngine.Line> toEngineLines(List<CartItem> cartItems,
            Map<String, FlashPriceView> flash) {
        List<PromotionEngine.Line> lines = new ArrayList<>(cartItems.size());
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            FlashPriceView view = flash == null ? null : flash.get(product.getId());
            lines.add(new PromotionEngine.Line(
                    product.getId(),
                    product.getCategory() != null ? product.getCategory().getId() : null,
                    product.getFactory(),
                    product.getPrice(),
                    (int) item.getQuantity(),
                    view == null ? null : view.flashPrice()));
        }
        return lines;
    }

    // ===== Tạo đơn =====

    @Transactional
    public OrderDetailResponse createOrder(String userId, CreateOrderRequest request) {
        // 1. Đơn phải dựng từ giỏ server — giỏ trống thì không cho đặt.
        Cart cart = this.cartService.getOrCreateCart(userId);
        List<CartItem> cartItems = cart.getItems().stream()
                .filter(item -> item.getProduct() != null)
                .toList();
        if (cartItems.isEmpty()) {
            throw new AppException(ErrorCode.CART_EMPTY);
        }

        User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // 2. Kiểm tra tồn kho LẦN CUỐI trước khi trừ — chặn trường hợp hàng đã
        //    bị khách khác mua hết sau khi sản phẩm được thêm vào giỏ.
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            if (!product.isActive()) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            if (item.getQuantity() > product.getQuantity()) {
                throw new AppException(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
            }
        }

        // 3. Trừ kho phiên flash TRƯỚC khi tính tiền (D29). Phải làm ở bước này
        //    vì kết quả trừ kho quyết định dòng nào còn được giá flash — mà giá
        //    flash lại là đầu vào của cả engine promotion lẫn eligibleAmount của
        //    voucher. Trừ sau khi tính tiền sẽ để lại phần giảm "mồ côi" của dòng
        //    đã mất flash (BR-F13).
        LocalDateTime now = LocalDateTime.now();
        List<String> cartProductIds = cartItems.stream().map(i -> i.getProduct().getId()).toList();
        Map<String, FlashPriceView> flashMap = this.flashSaleService.resolvePriceMap(cartProductIds, userId, now);

        // flashWonByProduct: dòng nào THỰC SỰ được giá flash (đã trừ kho thành công).
        Map<String, String> flashItemIdByProduct = new HashMap<>();
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            FlashPriceView view = flashMap.get(product.getId());
            if (view == null) {
                continue; // không có phiên → giá thường
            }
            // D32/BR-F11: khách đã hết suất cá nhân → dòng về giá thường. Phải bỏ
            // khỏi flashMap để engine promotion + eligibleAmount cũng tính theo giá
            // thường (BR-F13), không chỉ riêng detail.setPrice — nếu không, promotion
            // bị bỏ qua oan (D25) và subtotal lệch.
            if (!view.allowsFlashFor(item.getQuantity())) {
                flashMap.remove(product.getId());
                continue;
            }
            if (this.flashSaleService.consumeStock(view.itemId(), item.getQuantity())) {
                flashItemIdByProduct.put(product.getId(), view.itemId());
            } else {
                // D27 fallback: kho phiên cạn giữa chừng → dòng về giá thường.
                // Bỏ khỏi flashMap để engine + eligibleAmount tính theo giá thường.
                flashMap.remove(product.getId());
            }
        }

        // 4. Tính tiền TRÊN giá đã chốt. Flash sale đã xác định xong ở bước 3 nên
        //    giá ở đây là giá thật, không còn khả năng đổi nữa. Promotion chạy
        //    TRƯỚC trên từng dòng, voucher tính trên phần còn lại (D9).
        PromotionEngine.Result promo = this.promotionEngine.resolve(
                toEngineLines(cartItems, flashMap), this.promotionService.findApplicable(now), now);

        // D19: tăng usedCount BẰNG UPDATE atomic ngay trong transaction tạo đơn.
        // 0 dòng bị ảnh hưởng = chương trình vừa hết lượt vì khách khác chốt
        // song song → ném lỗi; @Transactional rollback nên không để lại lượt
        // đã tăng cho các promotion khác của cùng đơn này.
        for (Promotion applied : promo.appliedPromotions()) {
            if (this.promotionRepository.incrementUsedCount(applied.getId()) == 0) {
                throw new AppException(ErrorCode.PROMOTION_OUT_OF_STOCK);
            }
        }

        long subtotal = promo.subtotal();
        long promotionDiscount = promo.promotionDiscount();

        // Map kết quả engine theo productId — mỗi dòng giỏ đúng 1 dòng kết quả.
        // Dựng sớm vì cả tính voucher (D22) lẫn snapshot từng dòng đều cần.
        Map<String, PromotionEngine.LineResult> promoByProduct = new HashMap<>();
        for (PromotionEngine.LineResult lr : promo.lines()) {
            promoByProduct.put(lr.productId(), lr);
        }

        // D22: mức giảm tính trên tiền hàng KHỚP PHẠM VI (đã trừ promotion của
        // dòng), không phải tổng giỏ. Dùng chung checkVoucherRules với preview ở
        // trang giỏ để hai đường không bao giờ lệch luật.
        List<VoucherService.EligibleLine> voucherLines = cartItems.stream()
                .map(i -> {
                    PromotionEngine.LineResult lr = promoByProduct.get(i.getProduct().getId());
                    return new VoucherService.EligibleLine(
                            i.getProduct().getId(),
                            i.getProduct().getCategory() == null ? null
                                    : i.getProduct().getCategory().getId(),
                            i.getProduct().getFactory(),
                            lr != null ? lr.lineTotal() : i.getProduct().getPrice() * i.getQuantity(),
                            lr != null ? lr.discount() : 0L);
                })
                .toList();

        // Voucher chỉ vào đơn qua VÍ (đã bỏ đường gõ mã tay).
        Voucher voucher = null;
        UserVoucher walletVoucher = null;
        long voucherDiscount = 0L;

        if (request.getUserVoucherId() != null && !request.getUserVoucherId().isBlank()) {
            walletVoucher = this.voucherWalletService.getUsableVoucher(userId, request.getUserVoucherId());
            voucher = walletVoucher.getVoucher();
        }

        if (voucher != null) {
            // Ném ĐÚNG mã lỗi của nhánh vi phạm (chưa tới ngày / hết lượt / đơn
            // chưa đủ tối thiểu…) thay vì gộp thành VOUCHER_NOT_USABLE chung chung
            // — trước đây biến lỗi bị vứt đi nên khách thấy lý do khác với preview.
            ErrorCode voucherError = checkVoucherRules(userId, voucher, voucherLines);
            if (voucherError != null) {
                throw new AppException(voucherError);
            }
            long eligible = this.voucherService.calculateEligibleAmount(voucher, voucherLines);
            voucherDiscount = this.voucherService.calculateDiscount(voucher, eligible);
        }
        // D10: tổng giảm của đơn không bao giờ vượt subtotal.
        long discountAmount = Math.min(promotionDiscount + voucherDiscount, subtotal);
        long shippingFee = this.cartService.calculateShippingFee(subtotal);
        long totalPrice = Math.max(0L, subtotal - discountAmount + shippingFee);

        // 4. Dựng Order + từng dòng vào OrderDetail.
        Order order = new Order();
        order.setOrderCode(generateUniqueOrderCode());
        order.setUser(user);
        order.setVoucher(voucher);
        order.setDiscountAmount(discountAmount);
        // Tách 2 nguồn giảm (D1) để admin và khách thấy rõ tiền đến từ đâu.
        order.setPromotionDiscount(promotionDiscount);
        order.setVoucherDiscount(voucherDiscount);
        order.setShippingFee(shippingFee);
        order.setTotalPrice(totalPrice);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(request.getPaymentMethod() == null ? PaymentMethod.COD : request.getPaymentMethod());
        // COD: khách trả tiền khi nhận hàng → đơn mới luôn PENDING.
        // VNPay (chưa triển khai) sẽ chuyển sang PAID qua callback.
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setReceiverFullName(request.getReceiverFullName().trim());
        order.setReceiverPhone(request.getReceiverPhone().trim());
        // Email optional: chuỗi rỗng/toàn khoảng trắng quy về null cho sạch dữ liệu.
        String receiverEmail = request.getReceiverEmail();
        order.setReceiverEmail(receiverEmail == null || receiverEmail.isBlank() ? null : receiverEmail.trim());
        order.setReceiverAddress(request.getReceiverAddress().trim());
        // Địa chỉ 2 cấp sau sáp nhập 2025 — lưu kèm code + name từ select của FE
        order.setReceiverProvinceCode(request.getReceiverProvinceCode());
        order.setReceiverProvinceName(request.getReceiverProvinceName());
        order.setReceiverCommuneCode(request.getReceiverCommuneCode());
        order.setReceiverCommuneName(request.getReceiverCommuneName());
        order.setNote(request.getNote());
        order.setOrderDate(LocalDateTime.now());

        List<OrderDetail> details = new ArrayList<>();
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            // Giá flash chỉ còn trong map nếu đã trừ kho thành công ở bước 3, nên
            // tới đây giá đã là giá chốt hạ — không đổi nữa (BR-F13).
            FlashPriceView flashView = flashMap.get(product.getId());

            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(product);
            detail.setQuantity(item.getQuantity());
            // D27 + D32: giá bán = flashPrice nếu còn hiệu lực VÀ khách chưa dùng
            // hết suất của mình, ngược lại giá thường.
            detail.setPrice(flashView != null && flashView.allowsFlashFor(item.getQuantity())
                    ? flashView.flashPrice() : product.getPrice());
            // Snapshot giá gốc để chi tiết đơn hiện gạch ngang khi dòng được giảm giá.
            detail.setOriginalPrice(product.getPrice());
            detail.setProductCode(product.getCode());
            detail.setProductName(product.getName());
            detail.setProductImage(product.getImage());

            // Nhớ item đã trừ kho để hủy đơn hoàn ĐÚNG suất vào phiên (D12).
            detail.setFlashSaleItemId(flashItemIdByProduct.get(product.getId()));

            // D2: snapshot giảm giá + id chương trình xuống TỪNG dòng. Promotion
            // tắt sau đó vẫn không làm sai đơn đã đặt.
            PromotionEngine.LineResult lr = promoByProduct.get(product.getId());
            if (lr != null && lr.discount() > 0L) {
                detail.setDiscountAmount(lr.discount());
                detail.setPromotionId(lr.promotion().getId());
            }
            details.add(detail);

            // 5. Trừ tồn kho + tăng lượt bán bằng UPDATE ATOMIC (BR-A04). Điều
            //    kiện `quantity >= qty` nằm trong câu lệnh nên DB tự chặn 2 đơn
            //    song song bán vượt hàng; 0 dòng = vừa bị khách khác mua hết.
            if (this.productRepository.deductStock(product.getId(), item.getQuantity()) == 0) {
                throw new AppException(ErrorCode.CART_QUANTITY_EXCEEDS_STOCK);
            }
        }
        order.setOrderDetails(details);

        // 6. Tăng lượt dùng voucher bằng UPDATE ATOMIC (BR-A03) — áp cho CẢ hai
        //    đường (ví + gõ tay) vì usageLimit là hạn mức toàn hệ thống. 0 dòng =
        //    voucher vừa hết lượt vì khách khác chốt song song → ném lỗi, rollback
        //    cả đơn (không được âm thầm bỏ voucher rồi thu nhiều tiền hơn).
        if (voucher != null && this.voucherRepository.incrementUsedCount(voucher.getId()) == 0) {
            throw new AppException(ErrorCode.VOUCHER_OUT_OF_STOCK);
        }

        Order saved = this.orderRepository.save(order);

        // 7. Đánh dấu voucher trong ví đã dùng, gắn luôn đơn để hủy thì hoàn (D12).
        if (walletVoucher != null) {
            this.voucherWalletService.markUsed(walletVoucher, saved);
        }

        // 8. Xóa giỏ — cùng transaction, nên nếu bước nào trên lỗi thì giỏ
        //    vẫn còn nguyên để khách thử lại.
        this.cartService.clearCartForOrder(cart);

        return toDetailResponse(saved);
    }

    // ===== Truy vấn =====

    @Transactional(readOnly = true)
    public Page<OrderSummaryResponse> getMyOrders(String userId, Pageable pageable) {
        return this.orderRepository.findByUserIdOrderByOrderDateDesc(userId, pageable)
                .map(this::toSummaryResponse);
    }

    /** Tra đơn theo id + chủ sở hữu → khách không xem được đơn người khác. */
    @Transactional(readOnly = true)
    public OrderDetailResponse getMyOrderDetail(String userId, String orderId) {
        Order order = this.orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return toDetailResponse(order);
    }

    // ===== Hủy đơn =====

    /**
     * Khách tự hủy đơn. Chỉ cho hủy khi CHƯA giao hàng (PENDING/CONFIRMED) và
     * phải HOÀN LẠI tồn kho + giảm lượt bán, nếu không kho sẽ bị trừ oan.
     */
    @Transactional
    public OrderDetailResponse cancelMyOrder(String userId, String orderId) {
        Order order = this.orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new AppException(ErrorCode.ORDER_CANNOT_CANCEL);
        }

        restoreStock(order);
        // D12: hoàn voucher về ví + usedCount của voucher/promotion. Chỉ chạy
        // được ở PENDING/CONFIRMED (state machine chặn ở trên) nên đơn COMPLETED
        // không bao giờ hoàn — đúng luật.
        restorePromotions(order);

        order.setStatus(OrderStatus.CANCELLED);
        // Đơn đã thanh toán (VNPay, chưa triển khai) thì đánh dấu cần hoàn tiền.
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.REFUNDED);
        }
        Order saved = this.orderRepository.save(order);
        return toDetailResponse(saved);
    }

    /**
     * Hủy đơn VNPay quá hạn chưa thanh toán — do job dọn đơn gọi. Khác
     * {@link #cancelMyOrder} ở chỗ không kiểm tra chủ sở hữu (job chạy hệ
     * thống) và luôn hoàn tồn kho.
     */
    @Transactional
    public void cancelExpiredOrder(Order order) {
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.CONFIRMED) {
            return; // đơn đã đổi trạng thái ở luồng khác thì bỏ qua
        }
        restoreStock(order);
        restorePromotions(order);
        order.setStatus(OrderStatus.CANCELLED);
        this.orderRepository.save(order);
    }

    /** Hoàn tồn kho cho mọi dòng của đơn — dùng khi hủy đơn. */
    private void restoreStock(Order order) {
        if (order.getOrderDetails() == null) {
            return;
        }
        for (OrderDetail detail : order.getOrderDetails()) {
            if (detail.getProduct() == null) {
                continue; // sản phẩm đã bị xóa cứng khỏi DB
            }
            // UPDATE atomic (BR-A04) — chặn `sold` dưới 0 nếu bị gọi lặp.
            this.productRepository.restoreStock(detail.getProduct().getId(), detail.getQuantity());
        }
    }

    /**
     * Hoàn phần khuyến mại khi hủy đơn (D12). Không làm thì khách mất voucher
     * oan và ngân sách promotion/voucher bị trừ oan.
     *
     * <p>
     * Gồm 3 việc, phải cùng transaction với việc đổi trạng thái đơn:
     * <ul>
     * <li>Voucher trong ví → {@code AVAILABLE} (còn hạn) hoặc {@code EXPIRED}</li>
     * <li>{@code Voucher.usedCount} −1</li>
     * <li>{@code Promotion.usedCount} −1 cho TỪNG chương trình đã áp</li>
     * <li>Kho phiên flash: hoàn suất đã trừ lúc chốt</li>
     * </ul>
     */
    private void restorePromotions(Order order) {
        this.voucherWalletService.releaseOnCancel(this.voucherWalletService.findByOrderId(order.getId()));

        if (order.getVoucher() != null) {
            // UPDATE atomic (BR-A03) — chặn usedCount xuống dưới 0 nếu bị gọi lặp.
            this.voucherRepository.decrementUsedCount(order.getVoucher().getId());
        }

        // Đếm distinct theo OrderDetail.promotionId: một chương trình có thể giảm
        // nhiều dòng nhưng usedCount chỉ tăng 1 lần cho cả đơn (D19).
        if (order.getOrderDetails() != null) {
            order.getOrderDetails().stream()
                    .map(OrderDetail::getPromotionId)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .forEach(this.promotionRepository::decrementUsedCount);

            // Hoàn suất flash đã trừ lúc chốt (V5 lưu id item vào dòng).
            for (OrderDetail detail : order.getOrderDetails()) {
                if (detail.getFlashSaleItemId() != null) {
                    this.flashSaleService.releaseStock(detail.getFlashSaleItemId(), detail.getQuantity());
                }
            }
        }
    }

    // ===== Thanh toán VNPay =====

    /**
     * Tra đơn để mở cổng thanh toán: phải thuộc đúng khách đang đăng nhập
     * (chống thanh toán hộ đơn người khác). Các điều kiện nghiệp vụ còn lại
     * (phương thức, trạng thái, hạn giữ đơn, số lần thử) do PaymentService quyết.
     */
    @Transactional(readOnly = true)
    public Order getOrderForPayment(String orderCode, String userId) {
        return this.orderRepository.findByOrderCodeAndUserId(orderCode, userId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
    }

    // ===== Quản lý đơn (admin) =====

    /**
     * Luồng trạng thái hợp lệ. Đơn chỉ đi tiến theo chuỗi
     * PENDING → CONFIRMED → SHIPPING → COMPLETED; CANCELLED là nhánh phụ chỉ
     * vào được khi đơn CHƯA giao (PENDING/CONFIRMED).
     *
     * Cố ý không cho nhảy cóc hay lùi trạng thái: đơn đã giao thì không hủy
     * được, đơn đã hủy thì không hồi phục.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPING, OrderStatus.CANCELLED),
            OrderStatus.SHIPPING, EnumSet.of(OrderStatus.COMPLETED),
            OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

    /** Danh sách đơn cho admin, lọc theo trạng thái / thanh toán / ngày / từ khóa. */
    @Transactional(readOnly = true)
    public Page<AdminOrderResponse> getOrdersForAdmin(
            OrderStatus status,
            PaymentStatus paymentStatus,
            String keyword,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Pageable pageable) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        return this.orderRepository
                .searchAdmin(status, paymentStatus, normalizedKeyword, fromDate, toDate, pageable)
                .map(this::toAdminSummaryResponse);
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrderDetailForAdmin(String orderId) {
        Order order = this.orderRepository.findWithDetailsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        return toAdminDetailResponse(order);
    }

    /** Thẻ thống kê đầu trang quản lý đơn. */
    @Transactional(readOnly = true)
    public OrderStatsResponse getOrderStats() {
        OrderStatsResponse stats = new OrderStatsResponse();
        long pending = this.orderRepository.countByStatus(OrderStatus.PENDING);
        long confirmed = this.orderRepository.countByStatus(OrderStatus.CONFIRMED);
        long shipping = this.orderRepository.countByStatus(OrderStatus.SHIPPING);
        long completed = this.orderRepository.countByStatus(OrderStatus.COMPLETED);
        long cancelled = this.orderRepository.countByStatus(OrderStatus.CANCELLED);

        stats.setPendingCount(pending);
        stats.setConfirmedCount(confirmed);
        stats.setShippingCount(shipping);
        stats.setCompletedCount(completed);
        stats.setCancelledCount(cancelled);
        stats.setTotalOrders(pending + confirmed + shipping + completed + cancelled);
        stats.setNeedsAction(pending);
        stats.setCompletedRevenue(this.orderRepository.sumTotalPriceByStatus(OrderStatus.COMPLETED));
        return stats;
    }

    /**
     * Admin đổi trạng thái 1 đơn. Chặn mọi bước chuyển không nằm trong
     * {@link #ALLOWED_TRANSITIONS}.
     *
     * Hủy đơn phải HOÀN LẠI tồn kho, nếu không kho bị trừ oan. Đơn COD giao
     * thành công coi như đã thu tiền → paymentStatus = PAID.
     */
    @Transactional
    public AdminOrderDetailResponse updateOrderStatus(String orderId, OrderStatus newStatus) {
        Order order = this.orderRepository.findWithDetailsById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));
        applyStatusChange(order, newStatus);
        return toAdminDetailResponse(this.orderRepository.save(order));
    }

    /** Đổi trạng thái nhiều đơn; trả về số đơn cập nhật thành công. */
    @Transactional
    public int bulkUpdateOrderStatus(OrderBulkStatusRequest request) {
        int updated = 0;
        for (String id : request.getIds()) {
            Order order = this.orderRepository.findWithDetailsById(id).orElse(null);
            if (order == null || !canTransition(order.getStatus(), request.getStatus())) {
                continue; // bỏ qua đơn không tồn tại hoặc bước chuyển không hợp lệ
            }
            applyStatusChange(order, request.getStatus());
            this.orderRepository.save(order);
            updated++;
        }
        return updated;
    }

    /** Áp thay đổi trạng thái + các hệ quả nghiệp vụ đi kèm. */
    private void applyStatusChange(Order order, OrderStatus newStatus) {
        if (!canTransition(order.getStatus(), newStatus)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATUS);
        }

        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(order);
            // D12: admin hủy đơn cũng phải hoàn voucher + usedCount + kho phiên,
            // y như khách tự hủy. Thiếu bước này thì khách mất voucher oan và
            // ngân sách promotion/voucher bị trừ oan.
            // An toàn trước gọi lặp: CANCELLED -> noneOf nên canTransition đã chặn.
            restorePromotions(order);
            if (order.getPaymentStatus() == PaymentStatus.PAID) {
                order.setPaymentStatus(PaymentStatus.REFUNDED);
            }
        }

        // COD giao xong = đã thu tiền mặt. VNPay sẽ set PAID qua callback riêng.
        if (newStatus == OrderStatus.COMPLETED && order.getPaymentMethod() == PaymentMethod.COD) {
            order.setPaymentStatus(PaymentStatus.PAID);
        }

        order.setStatus(newStatus);
    }

    private boolean canTransition(OrderStatus from, OrderStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    // ===== Mapping =====

    // ===== Kiểm tra voucher =====

    /**
     * Dùng cho trang giỏ: hỏi trước số tiền được giảm mà không tạo đơn.
     *
     * <p>
     * D14: KHÔNG nhận {@code orderTotal} từ FE nữa — BE tự đọc giỏ của khách và
     * tự chạy engine. FE gửi số tiền lên thì (a) sửa được giá, (b) con số preview
     * lệch với lúc chốt đơn vì thiếu kết quả promotion trên từng dòng.
     *
     * <p>
     * D22: mức giảm tính trên {@code eligibleAmount} (chỉ tiền hàng khớp phạm vi,
     * đã trừ promotion của dòng), không phải tổng giỏ.
     *
     * <p>
     * Voucher chỉ vào đơn qua VÍ — request bắt buộc có {@code userVoucherId}.
     * Vẫn trả HTTP 200 kèm cờ {@code valid} để FE hiện thông báo inline, nhưng
     * message lấy từ {@link ErrorCode} chi tiết, để preview và lúc chốt đơn nói
     * CÙNG một lý do.
     */
    @Transactional(readOnly = true)
    public VoucherValidationResponse validateVoucher(String userId, ValidateVoucherRequest request) {
        // Voucher chỉ vào đơn qua VÍ (đã bỏ đường gõ mã tay) — bắt buộc có
        // userVoucherId trỏ tới một voucher trong ví của chính khách.
        String userVoucherId = request.getUserVoucherId();
        if (userVoucherId == null || userVoucherId.isBlank()) {
            return VoucherValidationResponse.invalid(ErrorCode.USER_VOUCHER_NOT_FOUND.getMessage());
        }
        UserVoucher walletVoucher = this.userVoucherRepository
                .findByIdAndUserId(userVoucherId, userId).orElse(null);
        if (walletVoucher == null) {
            return VoucherValidationResponse.invalid(ErrorCode.USER_VOUCHER_NOT_FOUND.getMessage());
        }
        if (walletVoucher.getStatus() == UserVoucherStatus.USED) {
            return VoucherValidationResponse.invalid(ErrorCode.USER_VOUCHER_ALREADY_USED.getMessage());
        }
        if (!walletVoucher.isAvailableAt(LocalDateTime.now())) {
            return VoucherValidationResponse.invalid(ErrorCode.USER_VOUCHER_EXPIRED.getMessage());
        }
        Voucher voucher = walletVoucher.getVoucher();

        CartPricing pricing = this.cartService.priceCart(userId);
        ErrorCode error = checkVoucherRules(userId, voucher, pricing.linesForVoucher());
        if (error != null) {
            return VoucherValidationResponse.invalid(error.getMessage());
        }

        long eligible = this.voucherService.calculateEligibleAmount(voucher, pricing.linesForVoucher());
        long discount = this.voucherService.calculateDiscount(voucher, eligible);
        if (discount <= 0) {
            return VoucherValidationResponse.invalid(ErrorCode.VOUCHER_NO_DISCOUNT.getMessage());
        }
        // BR-V14: mệnh giá > tiền hàng thì voucher vẫn dùng được nhưng kẹp mức
        // giảm. Khách mất phần chênh mà không được hoàn — trả kèm để FE cảnh báo
        // (FE không có mệnh giá gốc của mã gõ tay nên không tự tính được, BR-V13).
        long nominal = this.voucherService.calculateNominalDiscount(voucher, eligible);
        long forfeited = Math.max(0L, nominal - discount);
        return VoucherValidationResponse.ok(voucher.getCode(), discount, forfeited);
    }

    /**
     * Các điều kiện nghiệp vụ của voucher — trả {@link ErrorCode} vi phạm đầu tiên,
     * hoặc {@code null} nếu hợp lệ. Dùng chung cho preview (lấy {@code getMessage()}
     * hiện inline) và chốt đơn (ném {@code AppException}) để hai đường không bao
     * giờ lệch luật VÀ không lệch cả lý do từ chối.
     */
    private ErrorCode checkVoucherRules(String userId, Voucher voucher,
            List<VoucherService.EligibleLine> lines) {
        if (!this.voucherService.isVoucherUsable(voucher)) {
            return resolveUnusableReason(voucher);
        }
        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartDate() != null && now.isBefore(voucher.getStartDate())) {
            return ErrorCode.VOUCHER_NOT_STARTED;
        }
        if (this.voucherService.hasReachedPerUserLimit(userId, voucher)) {
            return ErrorCode.VOUCHER_PER_USER_LIMIT_REACHED;
        }
        long eligible = this.voucherService.calculateEligibleAmount(voucher, lines);
        if (eligible <= 0) {
            return ErrorCode.VOUCHER_NO_ELIGIBLE_ITEM;
        }
        // D22: ngưỡng tối thiểu xét trên tiền hàng KHỚP PHẠM VI, không phải cả giỏ.
        if (voucher.getMinOrderValue() != null && eligible < voucher.getMinOrderValue()) {
            return ErrorCode.VOUCHER_MIN_ORDER_NOT_MET;
        }
        return null;
    }

    /**
     * Vì sao {@link VoucherService#isVoucherUsable} trả false — tách thành mã lỗi
     * cụ thể để khách biết đúng lý do (bị khoá / hết hạn / hết lượt) thay vì một
     * message gộp "hết hạn hoặc hết lượt" như trước.
     *
     * <p>
     * Thứ tự xét khớp {@code isVoucherUsable}: active → hạn → lượt.
     */
    private ErrorCode resolveUnusableReason(Voucher voucher) {
        if (voucher == null) {
            return ErrorCode.VOUCHER_NOT_FOUND;
        }
        if (!voucher.isActive()) {
            return ErrorCode.VOUCHER_INACTIVE;
        }
        if (voucher.getExpiryDate() != null
                && voucher.getExpiryDate().toLocalDate().isBefore(java.time.LocalDate.now())) {
            return ErrorCode.VOUCHER_EXPIRED;
        }
        return ErrorCode.VOUCHER_OUT_OF_STOCK;
    }

    // ===== Sinh mã đơn =====

    /** Sinh mã đơn chưa tồn tại; thử lại vài lần phòng trường hợp trùng. */
    private String generateUniqueOrderCode() {
        for (int attempt = 0; attempt < ORDER_CODE_MAX_ATTEMPTS; attempt++) {
            String code = ORDER_CODE_PREFIX + randomDigits();
            if (this.orderRepository.findByOrderCode(code).isEmpty()) {
                return code;
            }
        }
        throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    private String randomDigits() {
        StringBuilder sb = new StringBuilder(ORDER_CODE_DIGITS);
        for (int i = 0; i < ORDER_CODE_DIGITS; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    // ===== Mapping =====

    private OrderSummaryResponse toSummaryResponse(Order order) {
        OrderSummaryResponse res = new OrderSummaryResponse();
        res.setId(order.getId());
        res.setOrderCode(order.getOrderCode());
        res.setOrderDate(order.getOrderDate());
        res.setStatus(order.getStatus());
        res.setPaymentMethod(order.getPaymentMethod());
        res.setPaymentStatus(order.getPaymentStatus());
        res.setTotalPrice(order.getTotalPrice());

        List<OrderDetail> details = order.getOrderDetails() == null ? List.of() : order.getOrderDetails();
        res.setDistinctItemCount(details.size());
        res.setItemCount(details.stream().mapToLong(OrderDetail::getQuantity).sum());
        res.setProductNames(details.stream().map(OrderDetail::getProductName).toList());
        if (!details.isEmpty()) {
            res.setFirstProductName(details.get(0).getProductName());
            res.setFirstProductImage(details.get(0).getProductImage());
        }
        return res;
    }

    private OrderDetailResponse toDetailResponse(Order order) {
        OrderDetailResponse res = new OrderDetailResponse();
        res.setId(order.getId());
        res.setOrderCode(order.getOrderCode());
        res.setOrderDate(order.getOrderDate());
        res.setStatus(order.getStatus());
        res.setPaymentMethod(order.getPaymentMethod());
        res.setPaymentStatus(order.getPaymentStatus());
        res.setDiscountAmount(order.getDiscountAmount());
        // D1: tách 2 nguồn giảm để khách thấy "Giảm giá sản phẩm" và "Voucher"
        // riêng. Đơn cũ (trước Sprint 1) 2 cột này NULL → giữ null, FE phân biệt
        // "không có dữ liệu" với "0đ".
        res.setPromotionDiscount(order.getPromotionDiscount());
        res.setVoucherDiscount(order.getVoucherDiscount());
        res.setShippingFee(order.getShippingFee());
        res.setTotalPrice(order.getTotalPrice());
        res.setVoucherCode(order.getVoucher() != null ? order.getVoucher().getCode() : null);
        res.setReceiverFullName(order.getReceiverFullName());
        res.setReceiverPhone(order.getReceiverPhone());
        res.setReceiverEmail(order.getReceiverEmail());
        res.setReceiverAddress(order.getReceiverAddress());
        res.setReceiverProvinceCode(order.getReceiverProvinceCode());
        res.setReceiverProvinceName(order.getReceiverProvinceName());
        res.setReceiverCommuneCode(order.getReceiverCommuneCode());
        res.setReceiverCommuneName(order.getReceiverCommuneName());
        res.setNote(order.getNote());

        List<OrderDetailResponse.OrderItemResponse> items = new ArrayList<>();
        long subtotal = 0L;
        // Tiền hàng GỐC (chưa trừ gì) — tính thẳng price × quantity thay vì
        // lineTotal + discount, vì getLineTotal() có sàn 0 nên cộng ngược lại sẽ
        // sai khi dữ liệu bẩn (giảm > giá trị dòng).
        long totalBeforeDiscount = 0L;
        // Gộp tiền giảm theo promotionId: 1 chương trình có thể giảm nhiều dòng
        // nhưng khách chỉ cần thấy tổng của nó.
        Map<String, Long> discountByPromotion = new LinkedHashMap<>();
        if (order.getOrderDetails() != null) {
            for (OrderDetail detail : order.getOrderDetails()) {
                OrderDetailResponse.OrderItemResponse item = new OrderDetailResponse.OrderItemResponse();
                item.setProductId(detail.getProduct() != null ? detail.getProduct().getId() : null);
                item.setProductCode(detail.getProductCode());
                item.setProductName(detail.getProductName());
                item.setProductImage(detail.getProductImage());
                item.setPrice(detail.getPrice());
                item.setOriginalPrice(detail.getOriginalPrice());
                item.setQuantity(detail.getQuantity());
                item.setLineTotal(detail.getLineTotal());
                item.setDiscountAmount(detail.getDiscountAmount());
                items.add(item);
                subtotal += detail.getLineTotal();
                totalBeforeDiscount += (detail.getPrice() == null ? 0L : detail.getPrice()) * detail.getQuantity();
                if (detail.getPromotionId() != null) {
                    discountByPromotion.merge(detail.getPromotionId(),
                            detail.getDiscountAmountSafe(), Long::sum);
                }
            }
        }
        res.setItems(items);
        // subtotal suy ra từ các dòng: totalPrice = subtotal - discount + ship
        res.setSubtotal(subtotal);
        res.setTotalBeforeDiscount(totalBeforeDiscount);
        // Gộp tiền giảm theo promotionId để khách thấy đơn giảm nhờ chương trình
        // NÀO (không chỉ tổng) — cùng cách gộp với màn admin (G10).
        res.setPromotionLines(toClientPromotionLines(discountByPromotion));

        // Lịch sử giao dịch + rule thanh toán lại do BE quyết, FE không tự suy ra.
        res.setPayments(this.paymentService.getHistory(order.getId()).stream()
                .map(this::toPaymentAttemptResponse)
                .toList());
        ErrorCode blocked = this.paymentService.checkPayable(order);
        res.setCanRetryPayment(blocked == null);
        res.setRetryBlockedReason(blocked == null ? null : blocked.getMessage());
        return res;
    }

    private OrderDetailResponse.PaymentAttemptResponse toPaymentAttemptResponse(Payment payment) {
        OrderDetailResponse.PaymentAttemptResponse res = new OrderDetailResponse.PaymentAttemptResponse();
        res.setId(payment.getId());
        res.setTxnRef(payment.getTxnRef());
        res.setAttemptNo(payment.getAttemptNo());
        res.setStatus(payment.getStatus());
        res.setAmount(payment.getAmount());
        res.setResponseCode(payment.getResponseCode());
        res.setTransactionNo(payment.getTransactionNo());
        res.setBankCode(payment.getBankCode());
        res.setCreatedAt(payment.getCreatedAt());
        return res;
    }

    // viết gon lại Order để Admin biết của ai để xử lý
    private AdminOrderResponse toAdminSummaryResponse(Order order) {
        AdminOrderResponse res = new AdminOrderResponse();
        res.setId(order.getId());
        res.setOrderCode(order.getOrderCode());
        res.setOrderDate(order.getOrderDate());
        res.setStatus(order.getStatus());
        res.setPaymentMethod(order.getPaymentMethod());
        res.setPaymentStatus(order.getPaymentStatus());
        res.setTotalPrice(order.getTotalPrice());
        res.setReceiverFullName(order.getReceiverFullName());
        res.setReceiverPhone(order.getReceiverPhone());
        fillCustomer(res, order.getUser());

        List<OrderDetail> details = order.getOrderDetails() == null ? List.of() : order.getOrderDetails();
        res.setItemCount(details.stream().mapToLong(OrderDetail::getQuantity).sum());
        res.setProductNames(details.stream().map(OrderDetail::getProductName).toList());
        if (!details.isEmpty()) {
            res.setFirstProductName(details.get(0).getProductName());
            res.setFirstProductImage(details.get(0).getProductImage());
        }
        res.setAllowedNextStatuses(List.copyOf(
                ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of())));
        return res;
    }

    private AdminOrderDetailResponse toAdminDetailResponse(Order order) {
        AdminOrderDetailResponse res = new AdminOrderDetailResponse();
        res.setId(order.getId());
        res.setOrderCode(order.getOrderCode());
        res.setOrderDate(order.getOrderDate());
        res.setStatus(order.getStatus());
        res.setPaymentMethod(order.getPaymentMethod());
        res.setPaymentStatus(order.getPaymentStatus());
        res.setPaymentTxnRef(order.getPaymentTxnRef());
        res.setDiscountAmount(order.getDiscountAmount());
        res.setShippingFee(order.getShippingFee());
        res.setTotalPrice(order.getTotalPrice());
        // G10: tách 2 nguồn giảm cho admin đối soát (D1).
        res.setPromotionDiscount(order.getPromotionDiscount());
        res.setVoucherDiscount(order.getVoucherDiscount());
        res.setVoucherCode(order.getVoucher() != null ? order.getVoucher().getCode() : null);
        res.setReceiverFullName(order.getReceiverFullName());
        res.setReceiverPhone(order.getReceiverPhone());
        res.setReceiverEmail(order.getReceiverEmail());
        res.setReceiverAddress(order.getReceiverAddress());
        res.setNote(order.getNote());
        fillCustomer(res, order.getUser());

        List<AdminOrderDetailResponse.AdminOrderItemResponse> items = new ArrayList<>();
        long subtotal = 0L;
        // Tiền hàng GỐC (chưa trừ gì) — cùng lý do như bản client.
        long totalBeforeDiscount = 0L;
        // Gộp tiền giảm theo promotionId: 1 chương trình có thể giảm nhiều dòng
        // nhưng admin chỉ cần thấy tổng của nó (G10).
        Map<String, Long> discountByPromotion = new LinkedHashMap<>();
        if (order.getOrderDetails() != null) {
            for (OrderDetail detail : order.getOrderDetails()) {
                AdminOrderDetailResponse.AdminOrderItemResponse item = new AdminOrderDetailResponse.AdminOrderItemResponse();
                item.setProductId(detail.getProduct() != null ? detail.getProduct().getId() : null);
                item.setProductCode(detail.getProductCode());
                item.setProductName(detail.getProductName());
                item.setProductImage(detail.getProductImage());
                item.setPrice(detail.getPrice());
                item.setOriginalPrice(detail.getOriginalPrice());
                item.setQuantity(detail.getQuantity());
                item.setLineTotal(detail.getLineTotal());
                item.setDiscountAmount(detail.getDiscountAmount());
                items.add(item);
                subtotal += detail.getLineTotal();
                totalBeforeDiscount += (detail.getPrice() == null ? 0L : detail.getPrice()) * detail.getQuantity();
                if (detail.getPromotionId() != null) {
                    discountByPromotion.merge(detail.getPromotionId(),
                            detail.getDiscountAmountSafe(), Long::sum);
                }
            }
        }
        res.setItems(items);
        res.setPromotionLines(toPromotionLines(discountByPromotion));
        res.setSubtotal(subtotal);
        res.setTotalBeforeDiscount(totalBeforeDiscount);
        res.setAllowedNextStatuses(List.copyOf(
                ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of())));
        return res;
    }

    /**
     * Tra tên chương trình cho từng promotionId đã áp. Chương trình bị xóa khỏi
     * DB thì trả name = null — đơn cũ vẫn xem được, chỉ thiếu tên.
     */
    private List<AdminOrderDetailResponse.PromotionLine> toPromotionLines(Map<String, Long> discountById) {
        if (discountById.isEmpty()) {
            return List.of();
        }
        Map<String, String> nameById = this.promotionRepository.findAllById(discountById.keySet()).stream()
                .collect(Collectors.toMap(Promotion::getId, Promotion::getName));
        List<AdminOrderDetailResponse.PromotionLine> lines = new ArrayList<>(discountById.size());
        discountById.forEach((id, amount) -> {
            AdminOrderDetailResponse.PromotionLine line = new AdminOrderDetailResponse.PromotionLine();
            line.setPromotionId(id);
            line.setName(nameById.get(id));
            line.setDiscountAmount(amount);
            lines.add(line);
        });
        return lines;
    }

    /**
     * Bản rút gọn của {@link #toPromotionLines} cho trang khách: chỉ id + tên +
     * tiền giảm. Tra tên chương trình đã xóa → null (đơn cũ vẫn xem được).
     */
    private List<OrderDetailResponse.PromotionLine> toClientPromotionLines(Map<String, Long> discountById) {
        if (discountById.isEmpty()) {
            return List.of();
        }
        Map<String, String> nameById = this.promotionRepository.findAllById(discountById.keySet()).stream()
                .collect(Collectors.toMap(Promotion::getId, Promotion::getName));
        List<OrderDetailResponse.PromotionLine> lines = new ArrayList<>(discountById.size());
        discountById.forEach((id, amount) -> {
            OrderDetailResponse.PromotionLine line = new OrderDetailResponse.PromotionLine();
            line.setPromotionId(id);
            line.setName(nameById.get(id));
            line.setDiscountAmount(amount);
            lines.add(line);
        });
        return lines;
    }

    /** Gán thông tin khách hàng vào response admin — dùng chung cho cả 2 DTO. */
    private void fillCustomer(AdminOrderResponse res, User user) {
        if (user == null) {
            return;
        }
        res.setUserId(user.getId());
        res.setCustomerName(user.getFullName());
        res.setCustomerEmail(user.getEmail());
        res.setCustomerPhone(user.getPhone());
    }

    private void fillCustomer(AdminOrderDetailResponse res, User user) {
        if (user == null) {
            return;
        }
        res.setUserId(user.getId());
        res.setCustomerName(user.getFullName());
        res.setCustomerEmail(user.getEmail());
        res.setCustomerPhone(user.getPhone());
    }
}
