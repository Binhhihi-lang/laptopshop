package com.example.laptopshop.dto.response.Order;

import java.time.LocalDateTime;
import java.util.List;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * Đơn hàng dạng rút gọn cho bảng quản lý của admin.
 *
 * Khác {@code OrderSummaryResponse} của client ở chỗ có thêm thông tin khách
 * hàng (tên/email/SĐT) — admin cần biết đơn của ai để xử lý.
 */
@Getter
@Setter
public class AdminOrderResponse {

    private String id;
    private String orderCode;
    private LocalDateTime orderDate;

    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;

    private Long totalPrice;
    private long itemCount; // tổng số lượng sản phẩm

    // ===== Thông tin khách hàng =====
    private String userId;
    private String customerName; // User.fullName
    private String customerEmail;
    private String customerPhone; // User.phone (có thể null)

    // ===== Thông tin người nhận (có thể khác khách hàng) =====
    private String receiverFullName;
    private String receiverPhone;

    private String firstProductName;
    private String firstProductImage;
    private List<String> productNames;

    /** Trạng thái admin có thể chuyển tới từ trạng thái hiện tại (để render nút). */
    private List<OrderStatus> allowedNextStatuses;
}
