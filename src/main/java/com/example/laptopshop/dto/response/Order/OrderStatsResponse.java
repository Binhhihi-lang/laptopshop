package com.example.laptopshop.dto.response.Order;

import lombok.Getter;
import lombok.Setter;

/** Thẻ thống kê đơn hàng ở đầu trang quản lý của admin. */
@Getter
@Setter
public class OrderStatsResponse {

    private long totalOrders;
    private long pendingCount;
    private long confirmedCount;
    private long shippingCount;
    private long completedCount;
    private long cancelledCount;

    /** Doanh thu các đơn đã hoàn thành (chưa trừ đơn hủy vì hủy không tính doanh thu). */
    private long completedRevenue;

    /** Số đơn cần xử lý ngay = đơn đang chờ xác nhận. */
    private long needsAction;
}
