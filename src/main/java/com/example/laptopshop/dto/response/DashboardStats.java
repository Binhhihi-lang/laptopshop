package com.example.laptopshop.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

/**
 * Số liệu tổng hợp cho Bảng điều khiển (một endpoint duy nhất — BR-D01).
 *
 * <p>
 * Nhóm số liệu NGƯỜI DÙNG ({@code userCount}, {@code activeUserCount},
 * {@code newCustomerCount}) là {@code null} khi người gọi thiếu quyền
 * {@code READ_USER} (BR-D06) — STAFF không được xem số liệu tài khoản.
 */
@Getter
@Setter
public class DashboardStats {

    // ===== Người dùng (null nếu thiếu READ_USER) =====
    private Long userCount;
    private Long activeUserCount;
    private Long newCustomerCount; // đăng ký trong 7 ngày gần nhất

    // ===== Sản phẩm =====
    private Long productCount;
    private Long activeProductCount;
    private Long inactiveProductCount;
    private Long lowStockCount;
    private Long criticalStockCount; // tồn < 2
    private List<LowStockProduct> lowStockProducts;

    // ===== Danh mục =====
    private Long categoryCount;
    private Long activeCategoryCount;

    // ===== Voucher =====
    private Long voucherCount;
    private Long activeVoucherCount; // đang trong khoảng start..expiry
    private Long voucherExpiringSoonCount; // hết hạn trong 7 ngày

    // ===== Đơn hàng =====
    private Long totalOrderCount;
    private Long needsActionCount; // PENDING
    private Long ordersTodayCount;
    private Map<String, Long> ordersByStatus;

    // ===== Doanh thu (BR-D04: chỉ đơn COMPLETED) =====
    private Long revenueInRange;
    private Long revenuePrevRange;
    private Double revenueChangePercent; // null khi kỳ trước = 0
    private Long revenueToday;
    private Long revenueYesterday;

    // ===== Chuỗi theo ngày cho biểu đồ =====
    private List<DailyPoint> revenueSeries;
    private List<DailyPoint> previousRevenueSeries;

    // ===== Top bán chạy (BR-D05: đơn COMPLETED, theo kỳ) =====
    private List<TopProduct> topSellingProducts;

    // ===== Hiệu quả khuyến mại (BR-BL14: đơn KHÔNG huỷ, theo kỳ) =====
    private PromotionEffect promotionEffect;

    @Getter
    @Setter
    public static class PromotionEffect {
        /** Tổng chi phí = promotion + voucher + flash (tiền đã giảm). */
        private Long totalCost;
        private Long promotionCost;
        private Long voucherCost;
        private Long flashSaleCost;

        /** Số đơn trong kỳ và số đơn CÓ dùng ít nhất một ưu đãi. */
        private Long totalOrders;
        private Long discountedOrders;
        /** Tỉ lệ đơn có khuyến mại (%), 1 chữ số thập phân. */
        private Double discountedRate;

        /** Top chương trình khuyến mại hiệu quả (tiền giảm giảm dần). */
        private List<ProgramEffect> topPromotions;
        /** Top voucher hiệu quả. */
        private List<ProgramEffect> topVouchers;

        /** Hiệu quả flash sale trong kỳ. */
        private FlashSaleEffect flashSale;
    }

    /** Một chương trình (promotion/voucher) trong bảng xếp hạng hiệu quả. */
    @Getter
    @Setter
    public static class ProgramEffect {
        private String id;
        private String name; // tên promotion, hoặc mã voucher
        private Long orderCount; // số đơn đã áp
        private Long discountAmount; // tổng tiền đã giảm
    }

    @Getter
    @Setter
    public static class FlashSaleEffect {
        private Long orderCount;
        private Long quantitySold;
        private Long discountAmount; // tổng (giá gốc − giá flash) × số lượng
    }

    @Getter
    @Setter
    public static class LowStockProduct {
        private String id;
        private String code;
        private String name;
        private Long quantity;
        private String image;
    }

    /** Một điểm trên biểu đồ doanh thu: doanh thu + số đơn của một ngày. */
    @Getter
    @Setter
    public static class DailyPoint {
        private LocalDate date;
        private Long revenue;
        private Long orderCount;
    }

    /** Một dòng trong bảng xếp hạng bán chạy. */
    @Getter
    @Setter
    public static class TopProduct {
        private String productId;
        private String code;
        private String name;
        private String image;
        private Long quantitySold;
        private Long revenue;
    }
}
