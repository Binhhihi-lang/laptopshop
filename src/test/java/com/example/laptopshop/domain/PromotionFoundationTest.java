package com.example.laptopshop.domain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Test nền tảng dữ liệu Sprint 1 (module Promotion + Voucher).
 *
 * Trọng tâm: các field MỚI phải NULL-SAFE với đơn cũ.
 * `ddl-auto=update` chỉ thêm cột nullable, đơn cũ trong DB sẽ có NULL.
 * Nếu getter trả primitive `long` → NullPointerException khi Hibernate map.
 * Đây chính là rủi ro G2/D13 mà plan đã cảnh báo.
 *
 * Test thuần Java, không cần Spring context (giống ProductServiceTest).
 */
class PromotionFoundationTest {

    // ==================================================================
    // OrderDetail — L1: `price` đổi double → Long, thêm discountAmount
    // ==================================================================

    @Test
    void orderDetail_donCu_discountAmountNull_thiTraVe0_khongNPE() {
        OrderDetail detail = new OrderDetail();
        detail.setQuantity(2);
        detail.setPrice(15_000_000L);

        // Cột mới thêm vào bảng cũ → NULL với đơn đã đặt trước Sprint 1
        detail.setDiscountAmount(null);

        assertNull(detail.getDiscountAmount(), "Field thô vẫn là null (chưa ghi DB)");
        assertEquals(0L, detail.getDiscountAmountSafe(),
                "Getter null-safe phải trả 0 thay vì ném NPE");
    }

    @Test
    void orderDetail_lineTotal_tinhBangDonGia_tru_giamGiaCapDong() {
        OrderDetail detail = new OrderDetail();
        detail.setQuantity(2);
        detail.setPrice(15_000_000L);
        detail.setDiscountAmount(1_000_000L);

        // 2 × 15.000.000 − 1.000.000 = 29.000.000
        assertEquals(29_000_000L, detail.getLineTotal());
    }

    @Test
    void orderDetail_lineTotal_khiChuaCoGiamGia_thiBangDonGia_nhan_soLuong() {
        OrderDetail detail = new OrderDetail();
        detail.setQuantity(3);
        detail.setPrice(10_000_000L);
        detail.setDiscountAmount(null);

        assertEquals(30_000_000L, detail.getLineTotal());
    }

    @Test
    void orderDetail_lineTotal_khongAm_khiGiamGiaVuotDonGia() {
        // Dữ liệu bẩn: giảm nhiều hơn giá trị dòng → không được trả số âm
        OrderDetail detail = new OrderDetail();
        detail.setQuantity(1);
        detail.setPrice(5_000_000L);
        detail.setDiscountAmount(9_000_000L);

        assertEquals(0L, detail.getLineTotal(), "Sàn là 0, không cho âm tiền");
    }

    @Test
    void orderDetail_priceLaLong_khongMatPhanThapPhan_khiTinhTien() {
        // L1: trước đây `double` → sai số tích luỹ khi cộng nhiều dòng.
        // Long cho kết quả chính xác tuyệt đối.
        OrderDetail detail = new OrderDetail();
        detail.setQuantity(3);
        detail.setPrice(19_990_000L);
        detail.setDiscountAmount(0L);

        assertEquals(59_970_000L, detail.getLineTotal());
    }

    // ==================================================================
    // Order — promotionDiscount / voucherDiscount null-safe
    // ==================================================================

    @Test
    void order_donCu_haiFieldMoiNull_thiGetterTraVe0() {
        Order order = new Order();

        // Đơn cũ: cột mới chưa có giá trị
        order.setPromotionDiscount(null);
        order.setVoucherDiscount(null);

        assertEquals(0L, order.getPromotionDiscountSafe());
        assertEquals(0L, order.getVoucherDiscountSafe());
    }

    @Test
    void order_tongGiamGia_bangTongHaiNguon() {
        Order order = new Order();
        order.setPromotionDiscount(500_000L);
        order.setVoucherDiscount(200_000L);

        assertEquals(700_000L, order.getTotalDiscount());
    }

    @Test
    void order_tongGiamGia_khiMotNguonNull_vanTinhDung() {
        Order order = new Order();
        order.setPromotionDiscount(null);
        order.setVoucherDiscount(150_000L);

        assertEquals(150_000L, order.getTotalDiscount());
    }

    @Test
    void order_onCreate_ganMacDinh0ChoFieldNull() {
        // @PrePersist chạy trước khi INSERT → đảm bảo không bao giờ ghi NULL mới
        Order order = new Order();
        order.setPromotionDiscount(null);
        order.setVoucherDiscount(null);

        order.onCreate();

        assertEquals(0L, order.getPromotionDiscount());
        assertEquals(0L, order.getVoucherDiscount());
    }

    @Test
    void order_onCreate_khongGhiDe_giaTriDaCo() {
        Order order = new Order();
        order.setPromotionDiscount(300_000L);
        order.setVoucherDiscount(100_000L);

        order.onCreate();

        assertEquals(300_000L, order.getPromotionDiscount(), "Không được ghi đè giá trị thật");
        assertEquals(100_000L, order.getVoucherDiscount());
    }

    // ==================================================================
    // Coupon — 6 field mới mặc định an toàn cho coupon cũ
    // ==================================================================

    @Test
    void coupon_cu_chuaCoCouponType_thiMacDinhPUBLIC() {
        Coupon coupon = new Coupon();

        // Coupon tạo trước Sprint 1 không có coupon_type trong DB
        coupon.setCouponType(null);

        assertEquals(CouponType.PUBLIC, coupon.getCouponTypeOrDefault(),
                "Coupon cũ phải được coi là PUBLIC để không mất hiệu lực");
    }

    @Test
    void coupon_cu_chuaCoScopeType_thiMacDinhALL() {
        Coupon coupon = new Coupon();
        coupon.setScopeType(null);

        assertEquals(ScopeType.ALL, coupon.getScopeTypeOrDefault(),
                "Coupon cũ áp cho toàn bộ đơn → ALL");
    }

    @Test
    void coupon_hanMucMacDinh_null_nghiaLaKhongGioiHan() {
        Coupon coupon = new Coupon();

        // P3 đã chốt: null = không giới hạn
        assertNull(coupon.getMinOrderValue());
        assertNull(coupon.getMaxDiscountAmount());
        assertNull(coupon.getPerUserLimit());
    }

    @Test
    void coupon_haiHinhThucGiamGia_luuDung() {
        Coupon coupon = new Coupon();
        coupon.setDiscountPercent(15);
        coupon.setDiscountAmount(null);

        assertEquals(15, coupon.getDiscountPercent());
        assertNull(coupon.getDiscountAmount(), "Chỉ chọn 1 trong 2 hình thức");
    }
}
