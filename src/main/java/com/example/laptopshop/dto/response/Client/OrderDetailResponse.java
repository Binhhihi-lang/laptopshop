package com.example.laptopshop.dto.response.Client;

import java.time.LocalDateTime;
import java.util.List;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

/** Chi tiết đầy đủ 1 đơn hàng — dùng cho trang chi tiết đơn của khách. */
@Getter
@Setter
public class OrderDetailResponse {

    private String id;
    private String orderCode;
    private LocalDateTime orderDate;

    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;

    private Long subtotal; // tổng tiền hàng (trước giảm giá, chưa ship)
    private Long discountAmount;
    private Long shippingFee;
    private Long totalPrice;

    private String couponCode; // null nếu đơn không dùng mã

    private String receiverFullName;
    private String receiverPhone;
    private String receiverEmail;
    private String receiverAddress;
    private String receiverProvinceCode;
    private String receiverProvinceName;
    private String receiverCommuneCode;
    private String receiverCommuneName;
    private String note;

    private List<OrderItemResponse> items;

    /** Các lần thử thanh toán của đơn (mới nhất trước) — rỗng với đơn COD. */
    private List<PaymentAttemptResponse> payments;

    /**
     * Có được bấm "Thanh toán lại" hay không, và nếu không thì vì sao. FE không
     * tự suy ra rule này để tránh lệch với BE.
     */
    private boolean canRetryPayment;
    private String retryBlockedReason;

    /** 1 dòng sản phẩm trong đơn — dữ liệu đã snapshot tại thời điểm mua. */
    @Getter
    @Setter
    public static class OrderItemResponse {
        private String productId;
        private String productCode;
        private String productName;
        private String productImage;
        private Long price; // giá tại thời điểm mua
        private long quantity;
        private Long lineTotal; // price * quantity
        private Long discountAmount; // giảm từ khuyến mại cho dòng này (0 nếu không)
    }

    /** 1 lần thử thanh toán — dữ liệu cổng trả về đã lưu lại. */
    @Getter
    @Setter
    public static class PaymentAttemptResponse {
        private String id;
        private String txnRef;
        private int attemptNo;
        private PaymentStatus status;
        private Long amount;
        private String responseCode;
        private String transactionNo;
        private String bankCode;
        private LocalDateTime createdAt;
    }
}
