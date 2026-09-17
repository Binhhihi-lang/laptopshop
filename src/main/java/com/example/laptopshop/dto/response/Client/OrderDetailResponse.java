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
    private String receiverAddress;
    private String note;

    private List<OrderItemResponse> items;

    /** 1 dòng sản phẩm trong đơn — dữ liệu đã snapshot tại thời điểm mua. */
    @Getter
    @Setter
    public static class OrderItemResponse {
        private String productId;
        private String productCode;
        private String productName;
        private String productImage;
        private double price; // giá tại thời điểm mua
        private long quantity;
        private double lineTotal; // price * quantity
    }
}
