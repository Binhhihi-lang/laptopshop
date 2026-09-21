package com.example.laptopshop.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
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
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.Client.CreateOrderRequest;
import com.example.laptopshop.dto.request.Order.OrderBulkStatusRequest;
import com.example.laptopshop.dto.response.Client.CouponValidationResponse;
import com.example.laptopshop.dto.response.Client.OrderDetailResponse;
import com.example.laptopshop.dto.response.Client.OrderSummaryResponse;
import com.example.laptopshop.dto.response.Order.AdminOrderDetailResponse;
import com.example.laptopshop.dto.response.Order.AdminOrderResponse;
import com.example.laptopshop.dto.response.Order.OrderStatsResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.CouponRepository;
import com.example.laptopshop.repository.OrderRepository;
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

        // 3. Tính tiền: subtotal → coupon → phí ship → tổng.
        long subtotal = cartItems.stream()
                .mapToLong(item -> item.getProduct().getPrice() * item.getQuantity())
                .sum();

        Coupon coupon = resolveCoupon(request.getCouponCode());
        long discountAmount = coupon == null ? 0L : this.couponService.calculateDiscount(coupon, subtotal);
        long shippingFee = this.cartService.calculateShippingFee(subtotal);
        long totalPrice = Math.max(0L, subtotal - discountAmount + shippingFee);

        // 4. Dựng Order + snapshot từng dòng vào OrderDetail.
        Order order = new Order();
        order.setOrderCode(generateUniqueOrderCode());
        order.setUser(user);
        order.setCoupon(coupon);
        order.setDiscountAmount(discountAmount);
        order.setShippingFee(shippingFee);
        order.setTotalPrice(totalPrice);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentMethod(request.getPaymentMethod() == null ? PaymentMethod.COD : request.getPaymentMethod());
        // COD: khách trả tiền khi nhận hàng → đơn mới luôn PENDING.
        // VNPay (chưa triển khai) sẽ chuyển sang PAID qua callback.
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setReceiverFullName(request.getReceiverFullName().trim());
        order.setReceiverPhone(request.getReceiverPhone().trim());
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

            OrderDetail detail = new OrderDetail();
            detail.setOrder(order);
            detail.setProduct(product);
            detail.setQuantity(item.getQuantity());
            detail.setPrice(product.getPrice());
            detail.setProductCode(product.getCode());
            detail.setProductName(product.getName());
            detail.setProductImage(product.getImage());
            details.add(detail);

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
        res.setShippingFee(order.getShippingFee());
        res.setTotalPrice(order.getTotalPrice());
        res.setCouponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null);
        res.setReceiverFullName(order.getReceiverFullName());
        res.setReceiverPhone(order.getReceiverPhone());
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
                item.setLineTotal(detail.getPrice() * detail.getQuantity());
                items.add(item);
                subtotal += (long) (detail.getPrice() * detail.getQuantity());
            }
        }
        res.setItems(items);
        // subtotal suy ra từ các dòng: totalPrice = subtotal - discount + ship
        res.setSubtotal(subtotal);
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
                item.setLineTotal(detail.getPrice() * detail.getQuantity());
                items.add(item);
                subtotal += (long) (detail.getPrice() * detail.getQuantity());
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
