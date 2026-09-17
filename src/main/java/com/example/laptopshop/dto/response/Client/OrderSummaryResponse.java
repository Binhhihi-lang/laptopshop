package com.example.laptopshop.dto.response.Client;

import java.time.LocalDateTime;
import java.util.List;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;

import lombok.Getter;
import lombok.Setter;

/**
 * Đơn hàng dạng rút gọn cho danh sách "Đơn hàng của tôi".
 *
 * Kèm 1 ảnh + tên sản phẩm đầu tiên để render thumbnail trên thẻ đơn (mockup
 * hiển thị "Sản phẩm A và N sản phẩm khác").
 */
@Getter
@Setter
public class OrderSummaryResponse {

    private String id;
    private String orderCode;
    private LocalDateTime orderDate;

    private OrderStatus status;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;

    private Long totalPrice;
    private long itemCount; // tổng số lượng sản phẩm trong đơn
    private int distinctItemCount; // số dòng sản phẩm (để hiện "và N sản phẩm khác")

    private String firstProductName;
    private String firstProductImage;
    private List<String> productNames; // toàn bộ tên sản phẩm — dùng khi đồng bộ tồn kho/hoàn tiền
}
