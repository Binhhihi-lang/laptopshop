package com.example.laptopshop.dto.request.Dashboard;

/**
 * Khoảng thời gian của Bảng điều khiển. Mỗi kỳ có một KỲ LIỀN TRƯỚC cùng độ dài
 * để so sánh doanh thu (BR-D02).
 */
public enum DashboardRange {
    TODAY, // hôm nay — kỳ trước là cả ngày hôm qua
    LAST_7_DAYS, // 7 ngày gần nhất — kỳ trước là 7 ngày liền trước
    LAST_30_DAYS, // 30 ngày gần nhất (mặc định) — kỳ trước là 30 ngày liền trước
    THIS_MONTH // từ ngày 1 tháng này — kỳ trước là cùng số ngày của tháng trước
}
