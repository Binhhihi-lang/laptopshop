package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.laptopshop.domain.Voucher;
import com.example.laptopshop.domain.VoucherScope;
import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.ScopeType;
import com.example.laptopshop.mapper.VoucherMapper;
import com.example.laptopshop.repository.VoucherRepository;
import com.example.laptopshop.repository.OrderRepository;
import com.example.laptopshop.repository.UserVoucherRepository;
import com.example.laptopshop.service.VoucherService.EligibleLine;

/**
 * Test VoucherService
 * D22 ({@code eligibleAmount} — cơ sở tính giảm) và D15 (đếm perUserLimit).
 */
@ExtendWith(MockitoExtension.class)
class VoucherServiceTest {

    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private VoucherMapper voucherMapper;
    @Mock
    private UploadService uploadService;
    @Mock
    private UserVoucherRepository userVoucherRepository;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private VoucherService voucherService;

    private Voucher voucher;

    @BeforeEach
    void setUp() {
        voucher = new Voucher();
        voucher.setId("voucher-1");
        voucher.setCode("GIAM10");
        voucher.setDiscountPercent(10);
        voucher.setActive(true);
    }

    private EligibleLine line(String productId, String categoryId, String factory, long total,
            long promotionDiscount) {
        return new EligibleLine(productId, categoryId, factory, total, promotionDiscount);
    }

    private VoucherScope scope(ScopeType type, String value) {
        VoucherScope s = new VoucherScope();
        s.setTargetType(type);
        s.setTargetValue(VoucherScope.normalizeTargetValue(type, value));
        return s;
    }

    // ==================================================================
    // D22 — eligibleAmount: chỉ tiền hàng khớp phạm vi, đã trừ promotion
    // ==================================================================

    @Nested
    @DisplayName("calculateEligibleAmount (D22)")
    class EligibleAmount {

        @Test
        @DisplayName("scope ALL → cộng cả giỏ")
        void scopeAll() {
            voucher.setScopeType(ScopeType.ALL);

            long eligible = voucherService.calculateEligibleAmount(voucher,
                    List.of(line("p1", "c1", "ASUS", 10_000_000L, 0L),
                            line("p2", "c2", "Dell", 5_000_000L, 0L)));

            assertEquals(15_000_000L, eligible);
        }

        @Test
        @DisplayName("scopeType null (voucher cũ) → coi như ALL, hành vi không đổi (P3)")
        void scopeNullNhuAll() {
            voucher.setScopeType(null);

            long eligible = voucherService.calculateEligibleAmount(voucher,
                    List.of(line("p1", "c1", "ASUS", 7_000_000L, 0L)));

            assertEquals(7_000_000L, eligible);
        }

        @Test
        @DisplayName("scope CATEGORY → chỉ cộng dòng thuộc danh mục đó")
        void scopeCategory() {
            voucher.setScopeType(ScopeType.CATEGORY);
            voucher.getScopes().add(scope(ScopeType.CATEGORY, "cat-laptop-van-phong"));

            long eligible = voucherService.calculateEligibleAmount(voucher,
                    List.of(line("p1", "cat-laptop-van-phong", "ASUS", 10_000_000L, 0L),
                            line("p2", "cat-gaming", "ASUS", 30_000_000L, 0L)));

            assertEquals(10_000_000L, eligible);
        }

        @Test
        @DisplayName("Nhiều scope cùng loại → khớp BẤT KỲ dòng nào thì cộng")
        void nhieuScopeCungLoai() {
            voucher.setScopeType(ScopeType.CATEGORY);
            voucher.getScopes().add(scope(ScopeType.CATEGORY, "cat-laptop-van-phong"));
            voucher.getScopes().add(scope(ScopeType.CATEGORY, "cat-gaming"));

            long eligible = voucherService.calculateEligibleAmount(voucher,
                    List.of(line("p1", "cat-laptop-van-phong", "ASUS", 10_000_000L, 0L),
                            line("p2", "cat-gaming", "ASUS", 30_000_000L, 0L),
                            line("p3", "cat-phu-kien", "ASUS", 5_000_000L, 0L)));

            assertEquals(40_000_000L, eligible);
        }

        @Test
        @DisplayName("scope BRAND → so khớp hãng không phân biệt hoa thường")
        void scopeBrand() {
            voucher.setScopeType(ScopeType.BRAND);
            voucher.getScopes().add(scope(ScopeType.BRAND, "ASUS"));

            long eligible = voucherService.calculateEligibleAmount(voucher,
                    List.of(line("p1", "c1", "asus", 10_000_000L, 0L),
                            line("p2", "c2", "Dell", 30_000_000L, 0L)));

            assertEquals(10_000_000L, eligible);
        }

        @Test
        @DisplayName("scope PRODUCT → chỉ đúng 1 sản phẩm")
        void scopeProduct() {
            voucher.setScopeType(ScopeType.PRODUCT);
            voucher.getScopes().add(scope(ScopeType.PRODUCT, "p1"));

            long eligible = voucherService.calculateEligibleAmount(voucher,
                    List.of(line("p1", "c1", "ASUS", 10_000_000L, 0L),
                            line("p2", "c2", "ASUS", 30_000_000L, 0L)));

            assertEquals(10_000_000L, eligible);
        }

        @Test
        @DisplayName("Trừ phần promotion đã giảm trên dòng — không tính trùng tiền (D9)")
        void truPromotionCuaDong() {
            voucher.setScopeType(ScopeType.ALL);

            long eligible = voucherService.calculateEligibleAmount(voucher,
                    List.of(line("p1", "c1", "ASUS", 10_000_000L, 2_000_000L)));

            assertEquals(8_000_000L, eligible);
        }

        @Test
        @DisplayName("Khai báo scope mà không có dòng nào → không khớp gì")
        void thieuScopeValue() {
            voucher.setScopeType(ScopeType.CATEGORY);
            voucher.getScopes().clear();

            long eligible = voucherService.calculateEligibleAmount(voucher,
                    List.of(line("p1", "c1", "ASUS", 10_000_000L, 0L)));

            assertEquals(0L, eligible);
        }

        @Test
        @DisplayName("Giỏ rỗng/null → 0")
        void gioRong() {
            assertEquals(0L, voucherService.calculateEligibleAmount(voucher, List.of()));
            assertEquals(0L, voucherService.calculateEligibleAmount(voucher, null));
        }
    }

    // ==================================================================
    // D15 — perUserLimit đếm GỘP cả 2 nguồn (ví + mã gõ tay)
    // ==================================================================

    @Nested
    @DisplayName("hasReachedPerUserLimit (D15)")
    class PerUserLimit {

        @BeforeEach
        void setUpLimit() {
            voucher.setPerUserLimit(1);
        }

        @Test
        @DisplayName("Chưa dùng lần nào → chưa chạm trần")
        void chuaDung() {
            when(userVoucherRepository.countByUserIdAndVoucherId("u1", "voucher-1")).thenReturn(0L);
            when(orderRepository.countByUserIdAndVoucherIdAndStatusNot("u1", "voucher-1",
                    OrderStatus.CANCELLED)).thenReturn(0L);

            assertFalse(voucherService.hasReachedPerUserLimit("u1", voucher));
        }

        @Test
        @DisplayName("Đã gõ mã 1 lần (limit=1) → chạm trần")
        void daGoMa() {
            when(userVoucherRepository.countByUserIdAndVoucherId(anyString(), anyString())).thenReturn(0L);
            when(orderRepository.countByUserIdAndVoucherIdAndStatusNot("u1", "voucher-1",
                    OrderStatus.CANCELLED)).thenReturn(1L);

            assertTrue(voucherService.hasReachedPerUserLimit("u1", voucher));
        }

        @Test
        @DisplayName("Gộp 2 nguồn: gõ mã 1 lần + voucher trong ví 1 lần = 2 (chống lách)")
        void gopHaiNguon() {
            voucher.setPerUserLimit(2);
            when(userVoucherRepository.countByUserIdAndVoucherId("u1", "voucher-1")).thenReturn(1L);
            when(orderRepository.countByUserIdAndVoucherIdAndStatusNot("u1", "voucher-1",
                    OrderStatus.CANCELLED)).thenReturn(1L);

            assertTrue(voucherService.hasReachedPerUserLimit("u1", voucher));
        }

        @Test
        @DisplayName("Đơn CANCELLED bị loại khỏi phép đếm (hủy không mất lượt)")
        void huyDonKhongTinh() {
            // Repository nhận OrderStatus.CANCELLED làm tham số loại trừ — khẳng
            // định đúng tham số đó được truyền xuống, vì đó là toàn bộ cơ chế.
            when(userVoucherRepository.countByUserIdAndVoucherId(anyString(), anyString())).thenReturn(0L);
            when(orderRepository.countByUserIdAndVoucherIdAndStatusNot("u1", "voucher-1",
                    OrderStatus.CANCELLED)).thenReturn(0L);

            assertFalse(voucherService.hasReachedPerUserLimit("u1", voucher));
        }

        @Test
        @DisplayName("perUserLimit null → không giới hạn, không hỏi DB")
        void khongGioiHan() {
            voucher.setPerUserLimit(null);

            assertFalse(voucherService.hasReachedPerUserLimit("u1", voucher));
        }

        @Test
        @DisplayName("perUserLimit <= 0 → coi như không giới hạn (P3)")
        void gioiHanKhongDuong() {
            voucher.setPerUserLimit(0);

            assertFalse(voucherService.hasReachedPerUserLimit("u1", voucher));
        }

        @Test
        @DisplayName("userId null → không chặn (chưa đăng nhập)")
        void thieuUserId() {
            assertFalse(voucherService.hasReachedPerUserLimit(null, voucher));
        }
    }

    // ==================================================================
    // D16 — kho voucher claim được
    // ==================================================================

    @Nested
    @DisplayName("getClaimableVouchers (D16)")
    class Claimable {

        @Test
        @DisplayName("Chỉ trả voucher PUBLIC còn dùng được")
        void chiPublic() {
            Voucher publicVoucher = new Voucher();
            publicVoucher.setId("c-public");
            publicVoucher.setActive(true);
            publicVoucher.setVoucherType(com.example.laptopshop.domain.VoucherType.PUBLIC);

            Voucher assigned = new Voucher();
            assigned.setId("c-assigned");
            assigned.setActive(true);
            assigned.setVoucherType(com.example.laptopshop.domain.VoucherType.ASSIGNED);

            Voucher off = new Voucher();
            off.setId("c-off");
            off.setActive(false);
            off.setVoucherType(com.example.laptopshop.domain.VoucherType.PUBLIC);

            when(voucherRepository.findAll()).thenReturn(List.of(publicVoucher, assigned, off));
            lenient().when(voucherMapper.toResponse(any(Voucher.class))).thenReturn(null);

            var result = voucherService.getClaimableVouchers();

            assertEquals(1, result.size());
        }
    }

    // ==================================================================
    // calculateDiscount — trần maxDiscountAmount cho kiểu phần trăm
    // ==================================================================

    @Nested
    @DisplayName("calculateDiscount (trần giảm)")
    class CalculateDiscount {

        @Test
        @DisplayName("Kiểu % không trần → giảm đúng theo phần trăm")
        void percentWithoutCap() {
            voucher.setDiscountPercent(10);
            voucher.setDiscountAmount(null);
            voucher.setMaxDiscountAmount(null);

            assertEquals(9_000_000L, voucherService.calculateDiscount(voucher, 90_000_000L));
        }

        @Test
        @DisplayName("Kiểu % có trần → cắt ở trần, không giảm quá")
        void percentCappedAtMax() {
            voucher.setDiscountPercent(10);
            voucher.setDiscountAmount(null);
            voucher.setMaxDiscountAmount(2_000_000L);

            assertEquals(2_000_000L, voucherService.calculateDiscount(voucher, 90_000_000L));
        }

        @Test
        @DisplayName("Kiểu % dưới trần → giữ nguyên mức theo phần trăm")
        void percentBelowCapUnchanged() {
            voucher.setDiscountPercent(10);
            voucher.setDiscountAmount(null);
            voucher.setMaxDiscountAmount(5_000_000L);

            assertEquals(3_000_000L, voucherService.calculateDiscount(voucher, 30_000_000L));
        }

        @Test
        @DisplayName("Kiểu số tiền cố định → không bị trần ảnh hưởng")
        void fixedAmountIgnoresCap() {
            voucher.setDiscountPercent(null);
            voucher.setDiscountAmount(500_000L);
            voucher.setMaxDiscountAmount(100_000L);

            assertEquals(500_000L, voucherService.calculateDiscount(voucher, 30_000_000L));
        }

        @Test
        @DisplayName("Trần lớn hơn tiền hàng → không vượt quá tiền hàng")
        void neverExceedsOrderTotal() {
            voucher.setDiscountPercent(100);
            voucher.setDiscountAmount(null);
            voucher.setMaxDiscountAmount(999_999_999L);

            assertEquals(1_000_000L, voucherService.calculateDiscount(voucher, 1_000_000L));
        }
    }

    // ==================================================================
    // calculateNominalDiscount — mệnh giá để đo phần khách MẤT (BR-V14)
    // ==================================================================

    @Nested
    @DisplayName("calculateNominalDiscount (mệnh giá, không kẹp theo đơn)")
    class CalculateNominalDiscount {

        @Test
        @DisplayName("Kiểu số tiền cố định → trả đúng mệnh giá, KHÔNG kẹp theo đơn")
        void fixedAmountNotClampedToOrder() {
            voucher.setDiscountPercent(null);
            voucher.setDiscountAmount(500_000L);

            assertEquals(500_000L, voucherService.calculateNominalDiscount(voucher, 90_000L));
        }

        @Test
        @DisplayName("Kiểu % dưới trần → mệnh giá theo phần trăm, không mất gì")
        void percentBelowCap() {
            voucher.setDiscountPercent(10);
            voucher.setDiscountAmount(null);
            voucher.setMaxDiscountAmount(null);

            assertEquals(9_000_000L, voucherService.calculateNominalDiscount(voucher, 90_000_000L));
        }

        @Test
        @DisplayName("Kiểu % vượt trần → kẹp ở TRẦN (thiết kế, không phải mất mát)")
        void percentCappedAtMax() {
            voucher.setDiscountPercent(10);
            voucher.setDiscountAmount(null);
            voucher.setMaxDiscountAmount(2_000_000L);

            assertEquals(2_000_000L, voucherService.calculateNominalDiscount(voucher, 90_000_000L));
        }

        @Test
        @DisplayName("Kiểu % (≤100) không trần → không bao giờ mất, mệnh giá = số thực giảm")
        void percentWithoutCapNeverForfeits() {
            voucher.setDiscountPercent(100);
            voucher.setDiscountAmount(null);
            voucher.setMaxDiscountAmount(null);

            long nominal = voucherService.calculateNominalDiscount(voucher, 100_000L);
            long discount = voucherService.calculateDiscount(voucher, 100_000L);

            // % ≤ 100 nên mệnh giá không bao giờ vượt tiền hàng → phần mất = 0.
            assertEquals(100_000L, nominal);
            assertEquals(nominal, discount);
        }

        @Test
        @DisplayName("Kiểu % có trần → vẫn KHÔNG bao giờ mất (trần chỉ cắt bớt, không tạo mất mát)")
        void percentWithCapNeverForfeits() {
            voucher.setDiscountPercent(50);
            voucher.setDiscountAmount(null);
            voucher.setMaxDiscountAmount(500_000L);

            // Cùng một eligible cho cả hai hàm (đúng như validateVoucher chạy).
            // 50% của 300k = 150k < trần 500k → mệnh giá 150k = số thực giảm.
            assertEquals(150_000L, voucherService.calculateNominalDiscount(voucher, 300_000L));
            assertEquals(150_000L, voucherService.calculateDiscount(voucher, 300_000L));

            // 50% của 2tr = 1tr > trần 500k → mệnh giá kẹp ở trần 500k, và
            // calculateDiscount cũng trả 500k (trần < tiền hàng) → vẫn không mất.
            assertEquals(500_000L, voucherService.calculateNominalDiscount(voucher, 2_000_000L));
            assertEquals(500_000L, voucherService.calculateDiscount(voucher, 2_000_000L));
        }

        @Test
        @DisplayName("Voucher không hợp lệ → 0")
        void invalidVoucher() {
            assertEquals(0L, voucherService.calculateNominalDiscount(null, 100_000L));
            assertEquals(0L, voucherService.calculateNominalDiscount(voucher, 0L));
        }
    }
}
