package com.example.laptopshop.domain;

// Trạng thái đơn hàng — thay cho cột "status" dạng tinyint (0,1,2,3...) không rõ nghĩa
// trong dữ liệu PHP cũ. Dùng enum giúp code dễ đọc và tránh nhầm lẫn số nghĩa là gì.
public enum OrderStatus {
    PENDING, // Chờ xử lý
    CONFIRMED, // Đã xác nhận
    SHIPPING, // Đang giao hàng
    COMPLETED, // Hoàn thành
    CANCELLED // Đã hủy
}
