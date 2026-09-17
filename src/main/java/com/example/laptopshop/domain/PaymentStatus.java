package com.example.laptopshop.domain;

/**
 * Trạng thái thanh toán của đơn hàng — TÁCH RIÊNG khỏi {@link OrderStatus}
 * (trạng thái giao hàng). Một đơn có thể đang SHIPPING nhưng đã PAID (VNPay)
 * hoặc vẫn PENDING (COD — chỉ trả tiền khi nhận hàng).
 */
public enum PaymentStatus {
    PENDING, // Chưa thanh toán (mặc định, đúng cho COD tới khi giao xong)
    PAID, // Đã thanh toán
    FAILED, // Thanh toán thất bại
    REFUNDED // Đã hoàn tiền (khi hủy đơn đã thanh toán)
}
