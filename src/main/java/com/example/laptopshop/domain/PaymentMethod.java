package com.example.laptopshop.domain;

/**
 * Phương thức thanh toán của đơn hàng storefront.
 *
 * Hiện tại chỉ COD được triển khai thật. VNPAY đã giữ sẵn trong enum để khi
 * tích hợp cổng thanh toán không phải sửa cấu trúc dữ liệu (cột đã lưu dạng
 * chuỗi qua @Enumerated(EnumType.STRING)).
 */
public enum PaymentMethod {
    COD, // Thanh toán khi nhận hàng (mặc định)
    VNPAY // Cổng thanh toán VNPay (chưa triển khai)
}
