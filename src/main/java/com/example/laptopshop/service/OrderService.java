package com.example.laptopshop.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Cart;
import com.example.laptopshop.domain.CartItem;
import com.example.laptopshop.domain.Coupon;
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
import com.example.laptopshop.dto.request.Order.OrderBulkStatusRequest;
import com.example.laptopshop.dto.response.Client.CouponValidationResponse;
import com.example.laptopshop.dto.response.Client.FlashPriceView;
import com.example.laptopshop.dto.response.Client.OrderDetailResponse;
import com.example.laptopshop.dto.response.Client.OrderSummaryResponse;
import com.example.laptopshop.dto.response.Order.AdminOrderDetailResponse;
import com.example.laptopshop.dto.response.Order.AdminOrderResponse;
import com.example.laptopshop.dto.response.Order.OrderStatsResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.CouponRepository;
import com.example.laptopshop.repository.OrderRepository;
import com.example.laptopshop.repository.PromotionRepository;
import com.example.laptopshop.repository.UserRepository;

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
    CouponRepository couponRepository;
    UserRepository userRepository;
    ProductService productService;
    CartService cartService;
    CouponService couponService;
    PaymentService paymentService;
    PromotionService promotionService;
    PromotionEngine promotionEngine;
    PromotionRepository promotionRepository;
    FlashSaleService flashSaleService;

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

        // 3. Tính tiền. Flash sale được tính lại NGAY LÚC CHỐT (D27): giá ở giỏ
        //    chỉ là preview, tới đây mới là giá thật. Promotion chạy TRƯỚC trên
        //    từng dòng, voucher tính trên phần còn lại (D9).
        LocalDateTime now = LocalDateTime.now();
        List<String> cartProductIds = cartItems.stream().map(i -> i.getProduct().getId()).toList();
        Map<String, FlashPriceView> flashMap = this.flashSaleService.resolvePriceMap(cartProductIds, userId, now);
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

        Coupon coupon = resolveCoupon(request.getCouponCode());
        long voucherDiscount = coupon == null ? 0L
                : this.couponService.calculateDiscount(coupon, subtotal - promotionDiscount);
        // D10: tổng giảm của đơn không bao giờ vượt subtotal.
        long discountAmount = Math.min(promotionDiscount + voucherDiscount, subtotal);
        long shippingFee = this.cartService.calculateShippingFee(subtotal);
        long totalPrice = Math.max(0L, subtotal - discountAmount + shippingFee);

        // 4. Dựng Order + snapshot từng dòng vào OrderDetail.
        Order order = new Order();
        order.setOrderCode(generateUniqueOrderCode());
        order.setUser(user);
        order.setCoupon(coupon);
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

        // Map kết quả engine theo productId — mỗi dòng giỏ đúng 1 dòng kết quả.
        Map<String, PromotionEngine.LineResult> promoByProduct = new HashMap<>();
        for (PromotionEngine.LineResult lr : promo.lines()) {
            promoByProduct.put(lr.productId(), lr);
        }

        List<OrderDetail> details = new ArrayList<>();
        for (CartItem item : cartItems) {
            Product product = item.getProduct();
            FlashPriceView flashView = flashMap.get(product.getId());

            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(product);
            detail.setQuantity(item.getQuantity());
            // D27 + D32: giá bán = flashPrice nếu còn hiệu lực VÀ khách chưa dùng
            // hết suất của mình, ngược lại giá thường.
            detail.setPrice(flashView != null && flashView.allowsFlashFor(item.getQuantity())
                    ? flashView.flashPrice() : product.getPrice());
            detail.setProductCode(product.getCode());
            detail.setProductName(product.getName());
            detail.setProductImage(product.getImage());

            // D2: snapshot giảm giá + id chương trình xuống TỪNG dòng. Promotion
            // tắt sau đó vẫn không làm sai đơn đã đặt.
            PromotionEngine.LineResult lr = promoByProduct.get(product.getId());
            if (lr != null && lr.discount() > 0L) {
                detail.setDiscountAmount(lr.discount());
                detail.setPromotionId(lr.promotion().getId());
            }
            details.add(detail);

            // D29: trừ kho phiên atomic. Cạn giữa chừng → fallback giá thường.
            if (flashView != null && flashView.allowsFlashFor(item.getQuantity())) {
                boolean consumed = this.flashSaleService.consumeStock(flashView.itemId(), item.getQuantity());
                if (!consumed) {
                    // D27 fallback: hết flashStock, dòng về giá thường, không fail đơn.
                    detail.setPrice(product.getPrice());
                    detail.setDiscountAmount(0L);
                    detail.setPromotionId(null);
                }
            }

            // 5. Trừ tồn kho + tăng lượt bán. save() để @LastModifiedDate ghi
            //    updatedAt và đảm bảo UPDATE phát ra ngay trong transaction này.
            product.setQuantity(product.getQuantity() - item.getQuantity());
            product.setSold(product.getSold() + item.getQuantity());
            this.productService.saveProduct(product);
        }
        order.setOrderDetails(details);

        // 6. Tăng lượt dùng coupon — chỉ tăng khi đơn thực sự được tạo.
        if (coupon != null) {
            coupon.setUsedCount(coupon.getUsedCount() == null ? 1 : coupon.getUsedCount() + 1);
            this.couponRepository.save(coupon);
        }

        Order saved = this.orderRepository.save(order);

        // 7. Xóa giỏ — cùng transaction, nên nếu bước nào trên lỗi thì giỏ
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
        order.setStatus(OrderStatus.CANCELLED);
        this.orderRepository.save(order);
    }

    /** Hoàn tồn kho cho mọi dòng của đơn — dùng khi hủy đơn. */
    private void restoreStock(Order order) {
        if (order.getOrderDetails() == null) {
            return;
        }
        for (OrderDetail detail : order.getOrderDetails()) {
            Product product = detail.getProduct();
            if (product == null) {
                continue; // sản phẩm đã bị xóa cứng khỏi DB
            }
            product.setQuantity(product.getQuantity() + detail.getQuantity());
            product.setSold(Math.max(0L, product.getSold() - detail.getQuantity()));
            this.productService.saveProduct(product);
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

    // ===== Kiểm tra coupon =====

    /** Dùng cho trang giỏ: hỏi trước số tiền được giảm mà không tạo đơn. */
    @Transactional(readOnly = true)
    public CouponValidationResponse validateCoupon(String code, long orderTotal) {
        if (code == null || code.isBlank()) {
            return CouponValidationResponse.invalid("Vui lòng nhập mã giảm giá.");
        }

        Coupon coupon = this.couponRepository.findByCodeIgnoreCase(code.trim()).orElse(null);
        if (coupon == null) {
            return CouponValidationResponse.invalid("Mã giảm giá không tồn tại.");
        }
        if (!this.couponService.isCouponUsable(coupon)) {
            return CouponValidationResponse.invalid("Mã giảm giá đã hết hạn hoặc hết lượt sử dụng.");
        }

        long discount = this.couponService.calculateDiscount(coupon, orderTotal);
        if (discount <= 0) {
            return CouponValidationResponse.invalid("Mã giảm giá không áp dụng được cho đơn này.");
        }
        return CouponValidationResponse.ok(coupon.getCode(), discount);
    }

    /**
     * Tra coupon để áp vào đơn. Khác {@link #validateCoupon}: mã SAI ở đây ném
     * lỗi thay vì trả về invalid, vì lúc này khách đã bấm "Đặt hàng" — không
     * được âm thầm bỏ qua mã và thu nhiều tiền hơn khách tưởng.
     */
    private Coupon resolveCoupon(String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return null;
        }
        Coupon coupon = this.couponRepository.findByCodeIgnoreCase(couponCode.trim())
                .orElseThrow(() -> new AppException(ErrorCode.COUPON_NOT_USABLE));
        if (!this.couponService.isCouponUsable(coupon)) {
            throw new AppException(ErrorCode.COUPON_NOT_USABLE);
        }
        return coupon;
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
        res.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
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
        if (order.getOrderDetails() != null) {
            for (OrderDetail detail : order.getOrderDetails()) {
                OrderDetailResponse.OrderItemResponse item = new OrderDetailResponse.OrderItemResponse();
                item.setProductId(detail.getProduct() != null ? detail.getProduct().getId() : null);
                item.setProductCode(detail.getProductCode());
                item.setProductName(detail.getProductName());
                item.setProductImage(detail.getProductImage());
                item.setPrice(detail.getPrice());
                item.setQuantity(detail.getQuantity());
                item.setLineTotal(detail.getLineTotal());
                item.setDiscountAmount(detail.getDiscountAmount());
                items.add(item);
                subtotal += detail.getLineTotal();
            }
        }
        res.setItems(items);
        // subtotal suy ra từ các dòng: totalPrice = subtotal - discount + ship
        res.setSubtotal(subtotal);

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
        res.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
        res.setReceiverFullName(order.getReceiverFullName());
        res.setReceiverPhone(order.getReceiverPhone());
        res.setReceiverEmail(order.getReceiverEmail());
        res.setReceiverAddress(order.getReceiverAddress());
        res.setNote(order.getNote());
        fillCustomer(res, order.getUser());

        List<AdminOrderDetailResponse.AdminOrderItemResponse> items = new ArrayList<>();
        long subtotal = 0L;
        if (order.getOrderDetails() != null) {
            for (OrderDetail detail : order.getOrderDetails()) {
                AdminOrderDetailResponse.AdminOrderItemResponse item = new AdminOrderDetailResponse.AdminOrderItemResponse();
                item.setProductId(detail.getProduct() != null ? detail.getProduct().getId() : null);
                item.setProductCode(detail.getProductCode());
                item.setProductName(detail.getProductName());
                item.setProductImage(detail.getProductImage());
                item.setPrice(detail.getPrice());
                item.setQuantity(detail.getQuantity());
                item.setLineTotal(detail.getLineTotal());
                item.setDiscountAmount(detail.getDiscountAmount());
                items.add(item);
                subtotal += detail.getLineTotal();
            }
        }
        res.setItems(items);
        res.setSubtotal(subtotal);
        res.setAllowedNextStatuses(List.copyOf(
                ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of())));
        return res;
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
