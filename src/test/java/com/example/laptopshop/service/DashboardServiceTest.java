package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.Dashboard.DashboardRange;
import com.example.laptopshop.dto.response.DashboardStats;
import com.example.laptopshop.repository.CategoryRepository;
import com.example.laptopshop.repository.OrderRepository;
import com.example.laptopshop.repository.ProductRepository;
import com.example.laptopshop.repository.UserRepository;
import com.example.laptopshop.repository.VoucherRepository;

/**
 * Test DashboardService — trọng tâm là 3 quy tắc dễ sai:
 * <ul>
 * <li>BR-D06: số liệu người dùng = null khi thiếu READ_USER (STAFF).</li>
 * <li>BR-D04/BR-D09: doanh thu chỉ đơn COMPLETED; % đổi = null khi kỳ trước 0.</li>
 * <li>BR-D02: kỳ so sánh cùng độ dài; chuỗi theo ngày điền đủ ngày trống = 0.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private com.example.laptopshop.repository.PromotionRepository promotionRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @BeforeEach
    void stubCommon() {
        // Các truy vấn đếm mặc định trả 0 để test nào không quan tâm cũng chạy được.
        lenient().when(productRepository.count()).thenReturn(0L);
        lenient().when(categoryRepository.count()).thenReturn(0L);
        lenient().when(voucherRepository.count()).thenReturn(0L);
        lenient().when(orderRepository.countGroupByStatus()).thenReturn(List.of());
        lenient().when(orderRepository.sumRevenueByDay(any(), any(), any())).thenReturn(List.of());
        lenient().when(orderRepository.findTopSellingProducts(any(), any(), any(), any())).thenReturn(List.of());
        // Khối khuyến mại: mặc định rỗng để test khác không vướng.
        lenient().when(orderRepository.sumDiscountCost(any(), any(), any())).thenReturn(List.of());
        lenient().when(orderRepository.countOrdersAndDiscounted(any(), any(), any())).thenReturn(List.of());
        lenient().when(orderRepository.sumPromotionEffect(any(), any(), any())).thenReturn(List.of());
        lenient().when(orderRepository.sumVoucherEffect(any(), any(), any())).thenReturn(List.of());
        lenient().when(orderRepository.sumFlashSaleEffect(any(), any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String... authorities) {
        var token = new UsernamePasswordAuthenticationToken(
                "tester", null,
                java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    // =========================================================================
    @Nested
    @DisplayName("BR-D06 — số liệu người dùng theo quyền READ_USER")
    class UserStats {

        @Test
        @DisplayName("Không có READ_USER (STAFF) → 3 trường người dùng = null")
        void staffGetsNullUserStats() {
            authenticate("READ_DASHBOARD"); // STAFF: có dashboard, thiếu READ_USER

            DashboardStats stats = dashboardService.getStats(DashboardRange.LAST_30_DAYS);

            assertNull(stats.getUserCount());
            assertNull(stats.getActiveUserCount());
            assertNull(stats.getNewCustomerCount());
        }

        @Test
        @DisplayName("Có READ_USER (ADMIN) → 3 trường người dùng có giá trị")
        void adminGetsUserStats() {
            authenticate("READ_DASHBOARD", "READ_USER");
            when(userRepository.count()).thenReturn(1284L);
            when(userRepository.countByActiveTrue()).thenReturn(1200L);
            when(userRepository.countByCreatedAtAfter(any())).thenReturn(24L);

            DashboardStats stats = dashboardService.getStats(DashboardRange.LAST_30_DAYS);

            assertEquals(1284L, stats.getUserCount());
            assertEquals(1200L, stats.getActiveUserCount());
            assertEquals(24L, stats.getNewCustomerCount());
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Doanh thu & % thay đổi")
    class Revenue {

        @Test
        @DisplayName("Kỳ trước = 0 → revenueChangePercent = null (không chia 0)")
        void nullPercentWhenPreviousZero() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.sumRevenueInRange(any(), any(), any())).thenReturn(50L, 0L);

            DashboardStats stats = dashboardService.getStats(DashboardRange.LAST_30_DAYS);

            assertEquals(50L, stats.getRevenueInRange());
            assertEquals(0L, stats.getRevenuePrevRange());
            assertNull(stats.getRevenueChangePercent());
        }

        @Test
        @DisplayName("Kỳ trước > 0 → % làm tròn 1 chữ số thập phân")
        void percentRoundedToOneDecimal() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.sumRevenueInRange(any(), any(), any())).thenReturn(110L, 100L);

            DashboardStats stats = dashboardService.getStats(DashboardRange.LAST_30_DAYS);

            assertEquals(10.0, stats.getRevenueChangePercent());
        }

        @Test
        @DisplayName("Chỉ tính đơn COMPLETED — kiểm tra trạng thái truyền xuống repo")
        void revenueUsesCompletedStatus() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.sumRevenueInRange(any(), any(), any())).thenReturn(0L);

            dashboardService.getStats(DashboardRange.TODAY);

            org.mockito.Mockito.verify(orderRepository, org.mockito.Mockito.atLeastOnce())
                    .sumRevenueInRange(org.mockito.ArgumentMatchers.eq(OrderStatus.COMPLETED), any(), any());
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Chuỗi doanh thu theo ngày (BR-D02)")
    class Series {

        @Test
        @DisplayName("Kỳ mặc định (null) = 30 ngày")
        void defaultRangeIs30Days() {
            authenticate("READ_DASHBOARD");

            DashboardStats stats = dashboardService.getStats(null);

            assertEquals(30, stats.getRevenueSeries().size());
            assertEquals(30, stats.getPreviousRevenueSeries().size());
        }

        @Test
        @DisplayName("Hôm nay → chuỗi 1 điểm")
        void todayHasSinglePoint() {
            authenticate("READ_DASHBOARD");

            DashboardStats stats = dashboardService.getStats(DashboardRange.TODAY);

            assertEquals(1, stats.getRevenueSeries().size());
        }

        @Test
        @DisplayName("Ngày không có đơn được điền 0, không bỏ trống")
        void missingDaysFilledWithZero() {
            authenticate("READ_DASHBOARD");
            LocalDate today = LocalDate.now();
            // Chỉ trả về 1 ngày có đơn; các ngày còn lại phải được bù 0.
            when(orderRepository.sumRevenueByDay(any(), any(), any()))
                    .thenReturn(List.<Object[]>of(new Object[] { java.sql.Date.valueOf(today), 5_000L, 2L }));

            DashboardStats stats = dashboardService.getStats(DashboardRange.LAST_7_DAYS);

            assertEquals(7, stats.getRevenueSeries().size());
            var last = stats.getRevenueSeries().get(6);
            assertEquals(today, last.getDate());
            assertEquals(5_000L, last.getRevenue());
            assertEquals(2L, last.getOrderCount());
            // Ngày đầu tiên không có đơn → 0
            assertEquals(0L, stats.getRevenueSeries().get(0).getRevenue());
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Đơn hàng theo trạng thái & top bán chạy")
    class OrdersAndTop {

        @Test
        @DisplayName("ordersByStatus luôn đủ 5 trạng thái, mặc định 0")
        void statusMapHasAllKeys() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.countGroupByStatus())
                    .thenReturn(List.<Object[]>of(new Object[] { OrderStatus.COMPLETED, 156L }));

            DashboardStats stats = dashboardService.getStats(DashboardRange.LAST_30_DAYS);

            Map<String, Long> byStatus = stats.getOrdersByStatus();
            assertEquals(5, byStatus.size());
            assertEquals(156L, byStatus.get("COMPLETED"));
            assertEquals(0L, byStatus.get("CANCELLED"));
            assertEquals(156L, stats.getTotalOrderCount());
        }

        @Test
        @DisplayName("Top bán chạy map đúng trường, gồm cả sản phẩm đã xoá (productId null)")
        void topSellingMapping() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.findTopSellingProducts(any(), any(), any(), any()))
                    .thenReturn(List.<Object[]>of(
                            new Object[] { "p1", "MBP14", "MacBook Pro 14", "img.jpg", 42L, 1_679_580_000L },
                            new Object[] { null, "OLD-1", "SP đã xoá", null, 5L, 10_000L }));

            DashboardStats stats = dashboardService.getStats(DashboardRange.LAST_30_DAYS);

            assertEquals(2, stats.getTopSellingProducts().size());
            var first = stats.getTopSellingProducts().get(0);
            assertEquals("p1", first.getProductId());
            assertEquals("MBP14", first.getCode());
            assertEquals(42L, first.getQuantitySold());
            assertEquals(1_679_580_000L, first.getRevenue());
            assertNull(stats.getTopSellingProducts().get(1).getProductId());
        }

        @Test
        @DisplayName("Danh sách sắp hết hàng map từ entity Product")
        void lowStockMapping() {
            authenticate("READ_DASHBOARD");
            Product p = new Product();
            p.setId("p9");
            p.setCode("MBA-M2");
            p.setName("MacBook Air M2");
            p.setQuantity(1);
            p.setImage("air.jpg");
            when(productRepository.findFirst5ByQuantityLessThanOrderByQuantityAsc(any(Integer.class)))
                    .thenReturn(List.of(p));

            DashboardStats stats = dashboardService.getStats(DashboardRange.LAST_30_DAYS);

            assertEquals(1, stats.getLowStockProducts().size());
            var dto = stats.getLowStockProducts().get(0);
            assertEquals("MBA-M2", dto.getCode());
            assertEquals(1L, dto.getQuantity());
            assertNotNull(dto.getImage());
            assertTrue(stats.getLowStockCount() >= 0);
        }
    }

    // =========================================================================
    @Nested
    @DisplayName("Hiệu quả khuyến mại (BR-BL14)")
    class PromotionEffectTest {

        @Test
        @DisplayName("Tổng chi phí = promotion + voucher + flash; loại đơn huỷ ở truy vấn")
        void totalCostSumsThreeSources() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.sumDiscountCost(any(), any(), any()))
                    .thenReturn(List.<Object[]>of(new Object[] { 5_000_000L, 2_000_000L }));
            when(orderRepository.sumFlashSaleEffect(any(), any(), any()))
                    .thenReturn(List.<Object[]>of(new Object[] { 3L, 4L, 1_500_000L }));

            var pe = dashboardService.getStats(DashboardRange.LAST_30_DAYS).getPromotionEffect();

            assertEquals(5_000_000L, pe.getPromotionCost());
            assertEquals(2_000_000L, pe.getVoucherCost());
            assertEquals(1_500_000L, pe.getFlashSaleCost());
            assertEquals(8_500_000L, pe.getTotalCost());
            assertEquals(3L, pe.getFlashSale().getOrderCount());
            assertEquals(4L, pe.getFlashSale().getQuantitySold());
        }

        @Test
        @DisplayName("Tỉ lệ đơn có khuyến mại; tổng đơn = 0 → null")
        void discountedRate() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.countOrdersAndDiscounted(any(), any(), any()))
                    .thenReturn(List.<Object[]>of(new Object[] { 19L, 8L }));

            var pe = dashboardService.getStats(DashboardRange.LAST_30_DAYS).getPromotionEffect();

            assertEquals(19L, pe.getTotalOrders());
            assertEquals(8L, pe.getDiscountedOrders());
            assertEquals(42.1, pe.getDiscountedRate()); // 8/19 = 42.105… → 42,1
        }

        @Test
        @DisplayName("Không có đơn nào → tỉ lệ null, không chia 0")
        void nullRateWhenNoOrders() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.countOrdersAndDiscounted(any(), any(), any()))
                    .thenReturn(List.<Object[]>of(new Object[] { 0L, 0L }));

            var pe = dashboardService.getStats(DashboardRange.LAST_30_DAYS).getPromotionEffect();

            assertNull(pe.getDiscountedRate());
        }

        @Test
        @DisplayName("Top chương trình xếp theo tiền giảm giảm dần và lấy tên từ Promotion")
        void topPromotionsSortedByNameResolved() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.sumPromotionEffect(any(), any(), any()))
                    .thenReturn(List.<Object[]>of(
                            new Object[] { "promo-A", 2L, 1_000_000L },
                            new Object[] { "promo-B", 5L, 9_000_000L }));
            var promo = new com.example.laptopshop.domain.Promotion();
            promo.setName("Khuyến mại A");
            when(promotionRepository.findById("promo-A")).thenReturn(java.util.Optional.of(promo));

            var tops = dashboardService.getStats(DashboardRange.LAST_30_DAYS)
                    .getPromotionEffect().getTopPromotions();

            assertEquals(2, tops.size());
            assertEquals("promo-B", tops.get(0).getId()); // tiền giảm cao hơn → đứng đầu
            assertEquals(9_000_000L, tops.get(0).getDiscountAmount());
            assertEquals("Khuyến mại A", tops.get(1).getName());
        }

        @Test
        @DisplayName("Top voucher lấy mã từ truy vấn, xếp theo tiền giảm")
        void topVouchersSorted() {
            authenticate("READ_DASHBOARD");
            when(orderRepository.sumVoucherEffect(any(), any(), any()))
                    .thenReturn(List.<Object[]>of(
                            new Object[] { "v1", "LAPTOP10", 3L, 300_000L },
                            new Object[] { "v2", "GIAM500K", 6L, 3_000_000L }));

            var tops = dashboardService.getStats(DashboardRange.LAST_30_DAYS)
                    .getPromotionEffect().getTopVouchers();

            assertEquals("GIAM500K", tops.get(0).getName());
            assertEquals(6L, tops.get(0).getOrderCount());
            assertEquals("LAPTOP10", tops.get(1).getName());
        }
    }
}
