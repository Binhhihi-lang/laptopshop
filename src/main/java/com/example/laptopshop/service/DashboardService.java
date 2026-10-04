package com.example.laptopshop.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.Dashboard.DashboardRange;
import com.example.laptopshop.dto.response.DashboardStats;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.CategoryRepository;
import com.example.laptopshop.repository.OrderRepository;
import com.example.laptopshop.repository.ProductRepository;
import com.example.laptopshop.repository.PromotionRepository;
import com.example.laptopshop.repository.UserRepository;
import com.example.laptopshop.repository.VoucherRepository;

/**
 * Tổng hợp số liệu cho Bảng điều khiển quản trị.
 *
 * <p>
 * Nguyên tắc (xem docs/ba/06-dashboard.md):
 * <ul>
 * <li>BR-D01: MỘT hàm trả về tất cả — không để FE gọi nhiều API rồi forkJoin.</li>
 * <li>BR-D04: doanh thu chỉ tính đơn {@code COMPLETED}.</li>
 * <li>BR-D06: nhóm số liệu NGƯỜI DÙNG trả {@code null} khi thiếu quyền
 * {@code READ_USER}.</li>
 * <li>BR-D10: chỉ đọc.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DashboardService {

    static final int LOW_STOCK_THRESHOLD = 5; // sắp hết
    static final int CRITICAL_STOCK_THRESHOLD = 2; // nguy hiểm
    static final int EXPIRING_SOON_DAYS = 7; // voucher sắp hết hạn
    static final int NEW_CUSTOMER_DAYS = 7; // khách mới
    static final int TOP_SELLING_LIMIT = 5;
    static final int TOP_PROGRAM_LIMIT = 5; // top chương trình khuyến mại

    UserRepository userRepository;
    ProductRepository productRepository;
    CategoryRepository categoryRepository;
    VoucherRepository voucherRepository;
    OrderRepository orderRepository;
    PromotionRepository promotionRepository;

    @Transactional(readOnly = true)
    public DashboardStats getStats(DashboardRange range) {
        DashboardRange effective = range == null ? DashboardRange.LAST_30_DAYS : range;
        LocalDateTime now = LocalDateTime.now();
        Period period = resolvePeriod(effective, now);

        DashboardStats stats = new DashboardStats();

        // ===== Người dùng — CHỈ khi có READ_USER (BR-D06) =====
        if (hasAuthority("READ_USER")) {
            stats.setUserCount(userRepository.count());
            stats.setActiveUserCount(userRepository.countByActiveTrue());
            stats.setNewCustomerCount(
                    userRepository.countByCreatedAtAfter(now.minusDays(NEW_CUSTOMER_DAYS)));
        }

        // ===== Sản phẩm =====
        stats.setProductCount(productRepository.count());
        stats.setActiveProductCount(productRepository.countByActiveTrue());
        stats.setInactiveProductCount(productRepository.countByActiveFalse());
        stats.setLowStockCount(productRepository.countByQuantityLessThan(LOW_STOCK_THRESHOLD));
        stats.setCriticalStockCount(productRepository.countByQuantityLessThan(CRITICAL_STOCK_THRESHOLD));

        List<Product> low = productRepository.findFirst5ByQuantityLessThanOrderByQuantityAsc(LOW_STOCK_THRESHOLD);
        stats.setLowStockProducts(low.stream().map(p -> {
            DashboardStats.LowStockProduct dto = new DashboardStats.LowStockProduct();
            dto.setId(p.getId());
            dto.setCode(p.getCode());
            dto.setName(p.getName());
            dto.setQuantity(p.getQuantity());
            dto.setImage(p.getImage());
            return dto;
        }).toList());

        // ===== Danh mục =====
        stats.setCategoryCount(categoryRepository.count());
        stats.setActiveCategoryCount(categoryRepository.countByActiveTrue());

        // ===== Voucher =====
        stats.setVoucherCount(voucherRepository.count());
        stats.setActiveVoucherCount(voucherRepository.countActiveVouchers(now));
        stats.setVoucherExpiringSoonCount(
                voucherRepository.countExpiringSoon(now, now.plusDays(EXPIRING_SOON_DAYS)));

        // ===== Đơn hàng =====
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OrderStatus s : OrderStatus.values()) {
            byStatus.put(s.name(), 0L);
        }
        for (Object[] row : orderRepository.countGroupByStatus()) {
            byStatus.put(((OrderStatus) row[0]).name(), (Long) row[1]);
        }
        stats.setOrdersByStatus(byStatus);
        stats.setTotalOrderCount(byStatus.values().stream().mapToLong(Long::longValue).sum());
        stats.setNeedsActionCount(byStatus.getOrDefault(OrderStatus.PENDING.name(), 0L));
        stats.setOrdersTodayCount(orderRepository.countByOrderDateGreaterThanEqualAndOrderDateLessThan(
                now.toLocalDate().atStartOfDay(), now.toLocalDate().plusDays(1).atStartOfDay()));

        // ===== Doanh thu (BR-D04) =====
        long revenue = orderRepository.sumRevenueInRange(
                OrderStatus.COMPLETED, period.from(), period.to());
        long revenuePrev = orderRepository.sumRevenueInRange(
                OrderStatus.COMPLETED, period.prevFrom(), period.prevTo());
        stats.setRevenueInRange(revenue);
        stats.setRevenuePrevRange(revenuePrev);
        stats.setRevenueChangePercent(percentChange(revenue, revenuePrev));

        stats.setRevenueToday(revenueOnDay(now.toLocalDate()));
        stats.setRevenueYesterday(revenueOnDay(now.toLocalDate().minusDays(1)));

        // ===== Chuỗi theo ngày =====
        stats.setRevenueSeries(buildSeries(period.from(), period.to()));
        stats.setPreviousRevenueSeries(buildSeries(period.prevFrom(), period.prevTo()));

        // ===== Top bán chạy (BR-D05) =====
        List<Object[]> top = orderRepository.findTopSellingProducts(
                OrderStatus.COMPLETED, period.from(), period.to(), PageRequest.of(0, TOP_SELLING_LIMIT));
        stats.setTopSellingProducts(top.stream().map(row -> {
            DashboardStats.TopProduct dto = new DashboardStats.TopProduct();
            dto.setProductId((String) row[0]);
            dto.setCode((String) row[1]);
            dto.setName((String) row[2]);
            dto.setImage((String) row[3]);
            dto.setQuantitySold(toLong(row[4]));
            dto.setRevenue(toLong(row[5]));
            return dto;
        }).toList());

        // ===== Hiệu quả khuyến mại (BR-BL14) =====
        stats.setPromotionEffect(buildPromotionEffect(period));

        return stats;
    }

    /** Hiệu quả khuyến mại trong kỳ — đơn KHÔNG huỷ (BR-BL14a). */
    private DashboardStats.PromotionEffect buildPromotionEffect(Period period) {
        DashboardStats.PromotionEffect effect = new DashboardStats.PromotionEffect();

        // Chi phí: promotion + voucher (2 cột trên Order)
        long promotionCost = 0;
        long voucherCost = 0;
        for (Object[] row : orderRepository.sumDiscountCost(
                OrderStatus.CANCELLED, period.from(), period.to())) {
            promotionCost = toLong(row[0]);
            voucherCost = toLong(row[1]);
        }

        // Flash sale: tính từ dòng chi tiết (giá gốc − giá flash)
        DashboardStats.FlashSaleEffect flash = new DashboardStats.FlashSaleEffect();
        flash.setOrderCount(0L);
        flash.setQuantitySold(0L);
        flash.setDiscountAmount(0L);
        for (Object[] row : orderRepository.sumFlashSaleEffect(
                OrderStatus.CANCELLED, period.from(), period.to())) {
            flash.setOrderCount(toLong(row[0]));
            flash.setQuantitySold(toLong(row[1]));
            flash.setDiscountAmount(toLong(row[2]));
        }
        effect.setFlashSale(flash);

        effect.setPromotionCost(promotionCost);
        effect.setVoucherCost(voucherCost);
        effect.setFlashSaleCost(flash.getDiscountAmount());
        effect.setTotalCost(promotionCost + voucherCost + flash.getDiscountAmount());

        // Tỉ lệ đơn có khuyến mại
        long totalOrders = 0;
        long discounted = 0;
        for (Object[] row : orderRepository.countOrdersAndDiscounted(
                OrderStatus.CANCELLED, period.from(), period.to())) {
            totalOrders = toLong(row[0]);
            discounted = toLong(row[1]);
        }
        effect.setTotalOrders(totalOrders);
        effect.setDiscountedOrders(discounted);
        effect.setDiscountedRate(totalOrders == 0
                ? null
                : Math.round(discounted * 1000.0 / totalOrders) / 10.0);

        // Top chương trình / voucher
        effect.setTopPromotions(topPromotions(period));
        effect.setTopVouchers(topVouchers(period));

        return effect;
    }

    /** Top promotion hiệu quả — tra tên từ bảng promotions, xếp theo tiền giảm. */
    private List<DashboardStats.ProgramEffect> topPromotions(Period period) {
        List<Object[]> rows = orderRepository.sumPromotionEffect(
                OrderStatus.CANCELLED, period.from(), period.to());
        return rows.stream()
                .map(row -> {
                    DashboardStats.ProgramEffect dto = new DashboardStats.ProgramEffect();
                    dto.setId((String) row[0]);
                    dto.setOrderCount(toLong(row[1]));
                    dto.setDiscountAmount(toLong(row[2]));
                    dto.setName(promotionRepository.findById(dto.getId())
                            .map(p -> p.getName() != null ? p.getName() : p.getTitle())
                            .orElse("(chương trình đã xoá)"));
                    return dto;
                })
                .sorted((a, b) -> Long.compare(b.getDiscountAmount(), a.getDiscountAmount()))
                .limit(TOP_PROGRAM_LIMIT)
                .toList();
    }

    /** Top voucher hiệu quả — mã voucher đã có sẵn trong câu truy vấn. */
    private List<DashboardStats.ProgramEffect> topVouchers(Period period) {
        List<Object[]> rows = orderRepository.sumVoucherEffect(
                OrderStatus.CANCELLED, period.from(), period.to());
        return rows.stream()
                .map(row -> {
                    DashboardStats.ProgramEffect dto = new DashboardStats.ProgramEffect();
                    dto.setId((String) row[0]);
                    dto.setName((String) row[1]);
                    dto.setOrderCount(toLong(row[2]));
                    dto.setDiscountAmount(toLong(row[3]));
                    return dto;
                })
                .sorted((a, b) -> Long.compare(b.getDiscountAmount(), a.getDiscountAmount()))
                .limit(TOP_PROGRAM_LIMIT)
                .toList();
    }

    // ===== Phụ trợ =====

    /** Doanh thu đơn COMPLETED của MỘT ngày (dùng cho thẻ "Doanh thu hôm nay"). */
    private long revenueOnDay(LocalDate day) {
        return orderRepository.sumRevenueInRange(
                OrderStatus.COMPLETED, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    /** Vẽ đủ ngày trong khoảng, ngày không có đơn thì điền 0 để biểu đồ liền mạch. */
    private List<DashboardStats.DailyPoint> buildSeries(LocalDateTime from, LocalDateTime to) {
        Map<LocalDate, long[]> byDay = new LinkedHashMap<>();
        for (Object[] row : orderRepository.sumRevenueByDay(OrderStatus.COMPLETED, from, to)) {
            byDay.put(toLocalDate(row[0]), new long[] { toLong(row[1]), toLong(row[2]) });
        }
        List<DashboardStats.DailyPoint> series = new ArrayList<>();
        LocalDate cursor = from.toLocalDate();
        LocalDate last = to.minusNanos(1).toLocalDate();
        while (!cursor.isAfter(last)) {
            DashboardStats.DailyPoint point = new DashboardStats.DailyPoint();
            point.setDate(cursor);
            long[] v = byDay.get(cursor);
            point.setRevenue(v == null ? 0L : v[0]);
            point.setOrderCount(v == null ? 0L : v[1]);
            series.add(point);
            cursor = cursor.plusDays(1);
        }
        return series;
    }

    /**
     * Kỳ đang chọn + kỳ liền trước CÙNG ĐỘ DÀI (BR-D02). Riêng TODAY so với cả
     * ngày hôm qua; THIS_MONTH lấy cùng SỐ NGÀY của tháng trước để hai kỳ cân nhau.
     */
    private Period resolvePeriod(DashboardRange range, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDateTime end = now.plusNanos(1); // mốc kết thúc nửa mở, gồm cả hiện tại
        return switch (range) {
            case TODAY -> {
                LocalDate start = today;
                LocalDate prevStart = today.minusDays(1);
                yield new Period(start.atStartOfDay(), end, prevStart.atStartOfDay(), start.atStartOfDay());
            }
            case LAST_7_DAYS -> rollingPeriod(today, end, 7);
            case LAST_30_DAYS -> rollingPeriod(today, end, 30);
            case THIS_MONTH -> {
                LocalDate start = today.withDayOfMonth(1);
                LocalDate prevStart = start.minusMonths(1);
                // Kỳ trước lấy ĐÚNG số ngày đã trôi qua của tháng này (kể cả hôm nay),
                // không lấy cả tháng — để hai kỳ cân nhau khi so sánh.
                long elapsed = start.until(today).getDays() + 1;
                LocalDate prevEnd = prevStart.plusDays(elapsed);
                yield new Period(start.atStartOfDay(), end, prevStart.atStartOfDay(), prevEnd.atStartOfDay());
            }
        };
    }

    private Period rollingPeriod(LocalDate today, LocalDateTime end, int days) {
        LocalDate start = today.minusDays(days - 1L);
        LocalDate prevStart = start.minusDays(days);
        return new Period(start.atStartOfDay(), end, prevStart.atStartOfDay(), start.atStartOfDay());
    }

    /** Phần trăm thay đổi; kỳ trước = 0 → null (tránh chia 0 — E-D01). */
    private Double percentChange(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        double pct = (current - previous) * 100.0 / previous;
        return Math.round(pct * 10) / 10.0; // 1 chữ số thập phân (BR-D09)
    }

    private boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet())
                .contains(authority);
    }

    private static Long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.util.Date date) {
            return new java.sql.Date(date.getTime()).toLocalDate();
        }
        throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
    }

    /** Khoảng thời gian nửa mở {@code [from, to)} của kỳ hiện tại và kỳ liền trước. */
    private record Period(LocalDateTime from, LocalDateTime to, LocalDateTime prevFrom, LocalDateTime prevTo) {
    }
}
