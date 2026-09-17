package com.example.laptopshop.dto.response.Client;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * Giỏ hàng trả về FE kèm sẵn phần tính tiền (subtotal / phí ship / tổng) để FE
 * không phải lặp lại công thức — tránh lệch số giữa giỏ và lúc đặt hàng.
 */
@Getter
@Setter
public class CartResponse {

    private String id;
    private List<CartItemResponse> items;

    // Số sản phẩm (tổng quantity) — dùng cho badge trên header.
    private long totalItems;

    private Long subtotal; // tổng tiền hàng
    private Long shippingFee; // 0 nếu được miễn phí
    private Long total; // subtotal + shippingFee (chưa trừ coupon)
    private Long freeShippingThreshold; // ngưỡng miễn phí ship — FE hiển thị gợi ý
}
