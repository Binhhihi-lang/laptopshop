package com.example.laptopshop.dto.response.Order;

import java.time.LocalDateTime;
import java.util.List;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

/** Chi tiết đầy đủ 1 đơn hàng — dùng cho trang chi tiết đơn của admin. */
@Getter
@Setter
public class AdminOrderDetailResponse {

    private String id;
    private String orderCode;
    private LocalDateTime orderDate;

    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;
    private String paymentTxnRef; // null với COD

    private Long subtotal; // tổng tiền hàng (trước giảm giá, chưa ship)
    private Long discountAmount;
    private Long shippingFee;
    private Long totalPrice;

    private String couponCode; // null nếu đơn không dùng mã

    // ===== Khách hàng =====
    private String userId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    // ===== Người nhận =====
    private String receiverFullName;
    private String receiverPhone;
    private String receiverEmail;
    private String receiverAddress;
    private String note;

    private List<AdminOrderItemResponse> items;

    /** Trạng thái admin có thể chuyển tới từ trạng thái hiện tại. */
    private List<OrderStatus> allowedNextStatuses;

    /** 1 dòng sản phẩm trong đơn — dữ liệu đã snapshot tại thời điểm mua. */
    @Getter
    @Setter
    public static class AdminOrderItemResponse {
        private String productId;
        private String productCode;
        private String productName;
        private String productImage;
        private double price; // giá tại thời điểm mua
        private long quantity;
        private double lineTotal; // price * quantity
    }
}
