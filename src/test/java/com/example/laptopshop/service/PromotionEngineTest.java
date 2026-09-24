package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.example.laptopshop.domain.Promotion;
import com.example.laptopshop.domain.PromotionDiscountType;
import com.example.laptopshop.domain.PromotionExclude;
import com.example.laptopshop.domain.PromotionScope;
import com.example.laptopshop.domain.ScopeType;
import com.example.laptopshop.service.PromotionEngine.Line;
import com.example.laptopshop.service.PromotionEngine.Result;

/**
 * Test {@link PromotionEngine} — hàm thuần nên không cần Spring context.
 *
 * <p>
 * Mỗi nhóm test neo vào một quyết định trong plan để khi đọc lại biết vì sao
 * con số kỳ vọng là như vậy. Các quyết định trọng tâm: D5 (1 dòng 1
 * promotion), D9 (promotion trước voucher), D10 (cap theo subtotal), D17
 * (scope ALL), D18 (BRAND so {@code Product.factory}), D21 (AMOUNT per-unit),
 * D23 (cap {@code maxDiscountAmount} theo tổng đơn), D25 (flash thắng).
 */
class PromotionEngineTest {

	private final PromotionEngine engine = new PromotionEngine();

	private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 24, 12, 0);
	private static final LocalDateTime START = NOW.minusDays(1);
	private static final LocalDateTime END = NOW.plusDays(1);

	// ==================================================================
	// Helpers — dựng entity ở trạng thái hợp lệ tối thiểu
	// ==================================================================

	private Promotion percent(int pct) {
		return promotion(PromotionDiscountType.PERCENT, pct);
	}

	private Promotion amount(long value) {
		return promotion(PromotionDiscountType.AMOUNT, value);
	}

	private Promotion fixedPrice(long value) {
		return promotion(PromotionDiscountType.FIXED_PRICE, value);
	}

	private Promotion promotion(PromotionDiscountType type, long value) {
		Promotion p = new Promotion();
		p.setId("p-" + type + "-" + value + "-" + System.nanoTime());
		p.setName("test");
		p.setDiscountType(type);
		p.setDiscountValue(value);
		p.setStartDate(START);
		p.setEndDate(END);
		p.setActive(true);
		p.setUsedCount(0);
		return p;
	}

	private Line line(long unitPrice, int qty) {
		return new Line("prod-1", "cat-1", "ASUS", unitPrice, qty);
	}

	private Result resolve(List<Line> lines, Promotion... promotions) {
		return engine.resolve(lines, List.of(promotions), NOW);
	}

	// ==================================================================
	// Biên: đầu vào rỗng / không có promotion
	// ==================================================================

	@Test
	@DisplayName("Giỏ rỗng → kết quả rỗng, không ném lỗi")
	void gioRong_traKetQuaRong() {
		Result result = engine.resolve(List.of(), List.of(), NOW);
		assertEquals(0L, result.subtotal());
		assertEquals(0L, result.promotionDiscount());
		assertTrue(result.lines().isEmpty());
	}

	@Test
	@DisplayName("Không có promotion nào → subtotal đúng, giảm 0, mỗi dòng giảm 0")
	void khongCoPromotion_giamBang0() {
		Result result = resolve(List.of(line(10_000_000L, 2)));
		assertEquals(20_000_000L, result.subtotal());
		assertEquals(0L, result.promotionDiscount());
		assertEquals(0L, result.lines().get(0).discount());
		assertNull(result.lines().get(0).promotion());
	}

	@Test
	@DisplayName("Danh sách promotion null → coi như rỗng, không NPE")
	void promotionsNull_khongNPE() {
		Result result = engine.resolve(List.of(line(5_000_000L, 1)), null, NOW);
		assertEquals(0L, result.promotionDiscount());
	}

	// ==================================================================
	// D21 — PERCENT tính trên tổng dòng, AMOUNT/FIXED_PRICE tính per-unit
	// ==================================================================

	@Nested
	@DisplayName("D21 — cấp tính tiền của từng loại")
	class CongThucTheoLoai {

		@Test
		@DisplayName("PERCENT: 8% của 20tr × 1 = 1.600.000")
		void percent_tinhTrenTongDong() {
			Result result = resolve(List.of(line(20_000_000L, 1)), percent(8));
			assertEquals(1_600_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("PERCENT: 10% của 20tr × 3 = 6.000.000 (theo tổng dòng)")
		void percent_nhanTheoSoLuongQuaTongDong() {
			Result result = resolve(List.of(line(20_000_000L, 3)), percent(10));
			assertEquals(6_000_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("AMOUNT: giảm 500k/máy × 3 máy = 1.500.000 (per-unit)")
		void amount_tinhPerUnit() {
			Result result = resolve(List.of(line(20_000_000L, 3)), amount(500_000L));
			assertEquals(1_500_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("FIXED_PRICE: giá cố định 15tr, giá gốc 20tr × 2 = giảm 10.000.000")
		void fixedPrice_tinhPerUnit() {
			Result result = resolve(List.of(line(20_000_000L, 2)), fixedPrice(15_000_000L));
			assertEquals(10_000_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("FIXED_PRICE cao hơn giá gốc → giảm 0, không âm")
		void fixedPrice_caoHonGiaGoc_giam0() {
			Result result = resolve(List.of(line(10_000_000L, 1)), fixedPrice(15_000_000L));
			assertEquals(0L, result.promotionDiscount());
		}

		@Test
		@DisplayName("PERCENT 150% bị kẹp về 100% → giảm đúng bằng giá dòng")
		void percent_tren100_biKepVe100() {
			Result result = resolve(List.of(line(10_000_000L, 1)), percent(150));
			assertEquals(10_000_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("discountValue = 0 hoặc null → không giảm")
		void discountValueKhongDuong_khongGiam() {
			Promotion zero = percent(0);
			assertEquals(0L, resolve(List.of(line(10_000_000L, 1)), zero).promotionDiscount());

			Promotion noValue = promotion(PromotionDiscountType.PERCENT, 10);
			noValue.setDiscountValue(null);
			assertEquals(0L, resolve(List.of(line(10_000_000L, 1)), noValue).promotionDiscount());
		}
	}

	// ==================================================================
	// R3 — không bao giờ giảm quá giá trị dòng / quá subtotal
	// ==================================================================

	@Nested
	@DisplayName("R3 — chặn giảm âm và giảm vượt")
	class ChanGiamQuaMuc {

		@Test
		@DisplayName("lineDiscount không bao giờ > lineTotal")
		void lineDiscount_khongVuotLineTotal() {
			// 100% + AMOUNT chồng nhau, chỉ 1 thắng → vẫn không vượt giá dòng
			Result result = resolve(List.of(line(10_000_000L, 1)), percent(100), amount(5_000_000L));
			assertEquals(10_000_000L, result.promotionDiscount());
			assertTrue(result.lines().get(0).discount() <= result.lines().get(0).lineTotal());
		}

		@Test
		@DisplayName("D10: tổng giảm không bao giờ > subtotal")
		void d10_tongGiamKhongVuotSubtotal() {
			Result result = resolve(List.of(line(10_000_000L, 1), line(5_000_000L, 1)), percent(100));
			assertEquals(15_000_000L, result.subtotal());
			assertEquals(15_000_000L, result.promotionDiscount());
			assertTrue(result.promotionDiscount() <= result.subtotal());
		}

		@Test
		@DisplayName("Tổng lineDiscount cộng lại đúng bằng promotionDiscount")
		void tongLineDiscount_khopPromotionDiscount() {
			Result result = resolve(List.of(line(20_000_000L, 1), line(10_000_000L, 2)), percent(10));
			long sum = result.lines().stream().mapToLong(l -> l.discount()).sum();
			assertEquals(result.promotionDiscount(), sum);
		}
	}

	// ==================================================================
	// D5 — mỗi dòng chỉ 1 promotion thắng: priority cao thắng, hoà thì giảm nhiều
	// ==================================================================

	@Nested
	@DisplayName("D5 — chọn promotion thắng trên mỗi dòng")
	class ChonPromotionThang {

		@Test
		@DisplayName("Priority cao thắng dù mức giảm nhỏ hơn")
		void priorityCaoThang_duGiamItHon() {
			Promotion nhoNhungUuTien = percent(5);
			nhoNhungUuTien.setPriority(10);
			Promotion lonNhungThap = percent(20);
			lonNhungThap.setPriority(1);

			Result result = resolve(List.of(line(20_000_000L, 1)), nhoNhungUuTien, lonNhungThap);
			assertEquals(1_000_000L, result.promotionDiscount(), "5% của 20tr thắng vì priority 10 > 1");
		}

		@Test
		@DisplayName("Hoà priority → mức giảm lớn hơn thắng")
		void hoaPriority_giamLonHonThang() {
			Promotion nho = percent(5);
			nho.setPriority(5);
			Promotion lon = percent(20);
			lon.setPriority(5);

			Result result = resolve(List.of(line(20_000_000L, 1)), nho, lon);
			assertEquals(4_000_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("Chỉ 1 promotion được ghi nhận trên mỗi dòng")
		void motDong_chiMotPromotion() {
			Promotion a = percent(10);
			a.setPriority(5);
			Promotion b = percent(10);
			b.setPriority(1);

			Result result = resolve(List.of(line(20_000_000L, 1)), a, b);
			assertEquals(1, result.appliedPromotions().size());
			assertEquals(a.getId(), result.lines().get(0).promotion().getId());
		}

		@Test
		@DisplayName("appliedPromotions() không trùng khi cùng promotion thắng nhiều dòng")
		void appliedPromotions_khongTrung() {
			Promotion p = percent(10);
			Result result = resolve(List.of(line(10_000_000L, 1), line(20_000_000L, 1)), p);
			assertEquals(1, result.appliedPromotions().size());
			assertEquals(3_000_000L, result.promotionDiscount());
		}
	}

	// ==================================================================
	// D17/D18 — scope: ALL / CATEGORY / BRAND / PRODUCT, và loại trừ thắng
	// ==================================================================

	@Nested
	@DisplayName("D17/D18 — scope và loại trừ")
	class ScopeVaLoaiTru {

		private Promotion withScope(ScopeType type, String value) {
			Promotion p = percent(10);
			PromotionScope scope = new PromotionScope();
			scope.setPromotion(p);
			scope.setTargetType(type);
			scope.setTargetValue(value);
			p.getScopes().add(scope);
			return p;
		}

		@Test
		@DisplayName("Không có scope → áp cả giỏ (ALL)")
		void khongCoScope_apCaGio() {
			Result result = resolve(List.of(line(10_000_000L, 1)), percent(10));
			assertEquals(1_000_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("scope ALL → áp cả giỏ")
		void scopeAll_apCaGio() {
			Result result = resolve(List.of(line(10_000_000L, 1)), withScope(ScopeType.ALL, "ignored"));
			assertEquals(1_000_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("scope CATEGORY khớp → áp; không khớp → bỏ qua")
		void scopeCategory_theoCategoryId() {
			Line khop = new Line("prod-1", "cat-1", "ASUS", 10_000_000L, 1);
			Line khongKhop = new Line("prod-2", "cat-2", "ASUS", 10_000_000L, 1);

			assertEquals(1_000_000L, resolve(List.of(khop), withScope(ScopeType.CATEGORY, "cat-1")).promotionDiscount());
			assertEquals(0L, resolve(List.of(khongKhop), withScope(ScopeType.CATEGORY, "cat-1")).promotionDiscount());
		}

		@Test
		@DisplayName("D18: scope BRAND so với Product.factory, không phân biệt hoa thường")
		void d18_scopeBrand_soVoiFactory() {
			Promotion p = withScope(ScopeType.BRAND, "ASUS");
			Line asus = new Line("prod-1", "cat-1", "Asus", 10_000_000L, 1);
			Line dell = new Line("prod-2", "cat-1", "Dell", 10_000_000L, 1);

			assertEquals(1_000_000L, resolve(List.of(asus), p).promotionDiscount());
			assertEquals(0L, resolve(List.of(dell), p).promotionDiscount());
		}

		@Test
		@DisplayName("scope PRODUCT chỉ áp đúng sản phẩm đó")
		void scopeProduct_theoProductId() {
			Promotion p = withScope(ScopeType.PRODUCT, "prod-1");
			Line khop = new Line("prod-1", "cat-1", "ASUS", 10_000_000L, 1);
			Line khongKhop = new Line("prod-9", "cat-1", "ASUS", 10_000_000L, 1);

			assertEquals(1_000_000L, resolve(List.of(khop), p).promotionDiscount());
			assertEquals(0L, resolve(List.of(khongKhop), p).promotionDiscount());
		}

		@Test
		@DisplayName("Loại trừ thắng scope: sản phẩm bị loại trừ không được giảm")
		void loaiTruThangScope() {
			Promotion p = percent(10);
			PromotionExclude exclude = new PromotionExclude();
			exclude.setPromotion(p);
			exclude.setProductId("prod-1");
			p.getExcludes().add(exclude);

			Line biLoaiTru = new Line("prod-1", "cat-1", "ASUS", 10_000_000L, 1);
			Line binhThuong = new Line("prod-2", "cat-1", "ASUS", 10_000_000L, 1);

			assertEquals(0L, resolve(List.of(biLoaiTru), p).promotionDiscount());
			assertEquals(1_000_000L, resolve(List.of(binhThuong), p).promotionDiscount());
		}

		@Test
		@DisplayName("Dòng không khớp scope vẫn được tính vào subtotal (điều kiện minOrderValue)")
		void dongKhongKhopScope_vanVaoSubtotal() {
			Promotion p = withScope(ScopeType.CATEGORY, "cat-1");
			p.setMinOrderValue(15_000_000L);
			Line khop = new Line("prod-1", "cat-1", "ASUS", 10_000_000L, 1);
			Line khongKhop = new Line("prod-2", "cat-2", "Dell", 10_000_000L, 1);

			// subtotal 20tr ≥ 15tr nên đủ điều kiện; chỉ dòng cat-1 được giảm
			Result result = resolve(List.of(khop, khongKhop), p);
			assertEquals(20_000_000L, result.subtotal());
			assertEquals(1_000_000L, result.promotionDiscount());
		}
	}

	// ==================================================================
	// Điều kiện hiệu lực: thời gian, usageLimit, minQuantity, minOrderValue
	// ==================================================================

	@Nested
	@DisplayName("Điều kiện hiệu lực")
	class DieuKienHieuLuc {

		@Test
		@DisplayName("Promotion inactive → không áp")
		void inactive_khongAp() {
			Promotion p = percent(10);
			p.setActive(false);
			assertEquals(0L, resolve(List.of(line(10_000_000L, 1)), p).promotionDiscount());
		}

		@Test
		@DisplayName("Chưa tới startDate → không áp")
		void chuaToiStartDate_khongAp() {
			Promotion p = percent(10);
			p.setStartDate(NOW.plusDays(1));
			p.setEndDate(NOW.plusDays(2));
			assertEquals(0L, resolve(List.of(line(10_000_000L, 1)), p).promotionDiscount());
		}

		@Test
		@DisplayName("Đã qua endDate → không áp")
		void quaEndDate_khongAp() {
			Promotion p = percent(10);
			p.setStartDate(NOW.minusDays(5));
			p.setEndDate(NOW.minusDays(1));
			assertEquals(0L, resolve(List.of(line(10_000_000L, 1)), p).promotionDiscount());
		}

		@Test
		@DisplayName("Đúng biên startDate/endDate → vẫn áp")
		void dungBien_thoiGian_vanAp() {
			Promotion p = percent(10);
			p.setStartDate(NOW);
			p.setEndDate(NOW);
			assertEquals(1_000_000L, resolve(List.of(line(10_000_000L, 1)), p).promotionDiscount());
		}

		@Test
		@DisplayName("Hết ngân sách (usedCount = usageLimit) → không áp")
		void hetNganSach_khongAp() {
			Promotion p = percent(10);
			p.setUsageLimit(5);
			p.setUsedCount(5);
			assertEquals(0L, resolve(List.of(line(10_000_000L, 1)), p).promotionDiscount());
		}

		@Test
		@DisplayName("usageLimit null = không giới hạn")
		void usageLimitNull_khongGioiHan() {
			Promotion p = percent(10);
			p.setUsageLimit(null);
			p.setUsedCount(1000);
			assertEquals(1_000_000L, resolve(List.of(line(10_000_000L, 1)), p).promotionDiscount());
		}

		@Test
		@DisplayName("minQuantity: mua 2, yêu cầu 3 → không áp; đủ 3 thì áp (10% của 30tr)")
		void minQuantity_khongDat_khongAp() {
			Promotion p = percent(10);
			p.setMinQuantity(3);
			assertEquals(0L, resolve(List.of(line(10_000_000L, 2)), p).promotionDiscount());
			assertEquals(3_000_000L, resolve(List.of(line(10_000_000L, 3)), p).promotionDiscount());
		}

		@Test
		@DisplayName("minOrderValue: 10tr < 15tr → không áp; đủ thì áp")
		void minOrderValue_theoSubtotal() {
			Promotion p = percent(10);
			p.setMinOrderValue(15_000_000L);
			assertEquals(0L, resolve(List.of(line(10_000_000L, 1)), p).promotionDiscount());
			assertEquals(2_000_000L, resolve(List.of(line(20_000_000L, 1)), p).promotionDiscount());
		}
	}

	// ==================================================================
	// D23 — cap maxDiscountAmount theo TỔNG các dòng cùng promotion
	// ==================================================================

	@Nested
	@DisplayName("D23 — ngân sách tối đa mỗi chương trình")
	class NganSachToiDa {

		@Test
		@DisplayName("Cap trên tổng đơn, không phải từng dòng")
		void capThemTongDon_khongPhaiTungDong() {
			Promotion p = percent(50);
			p.setMaxDiscountAmount(3_000_000L);

			// 50% của 20tr = 10tr, nhưng cap 3tr
			Result result = resolve(List.of(line(20_000_000L, 1)), p);
			assertEquals(3_000_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("Nhiều dòng cùng promotion: cap áp trên tổng, không nhân theo số dòng")
		void nhieuDong_cungPromotion_capTrenTong() {
			Promotion p = percent(50);
			p.setMaxDiscountAmount(3_000_000L);

			// 2 dòng, mỗi dòng 50% của 20tr = 10tr → tổng 20tr, cap 3tr
			Result result = resolve(List.of(line(20_000_000L, 1), line(20_000_000L, 1)), p);
			assertEquals(3_000_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("Sau khi cap, tổng lineDiscount vẫn khớp promotionDiscount")
		void sauCap_tongLineDiscount_vanKhop() {
			Promotion p = percent(50);
			p.setMaxDiscountAmount(3_000_000L);

			Result result = resolve(List.of(line(20_000_000L, 1), line(20_000_000L, 1)), p);
			long sum = result.lines().stream().mapToLong(l -> l.discount()).sum();
			assertEquals(result.promotionDiscount(), sum);
			assertEquals(3_000_000L, sum);
		}

		@Test
		@DisplayName("maxDiscountAmount null = không cap")
		void maxDiscountAmountNull_khongCap() {
			Promotion p = percent(50);
			p.setMaxDiscountAmount(null);
			assertEquals(10_000_000L, resolve(List.of(line(20_000_000L, 1)), p).promotionDiscount());
		}
	}

	// ==================================================================
	// D25 — flash sale thắng: dòng có flashPrice thì promotion bị bỏ qua
	// ==================================================================

	@Nested
	@DisplayName("D25 — flash sale thắng promotion")
	class FlashThang {

		@Test
		@DisplayName("Dòng có flashPrice → lineDiscount = 0")
		void dongCoFlash_khongApPromotion() {
			Line flash = new Line("prod-1", "cat-1", "ASUS", 20_000_000L, 1, 15_000_000L);
			Result result = resolve(List.of(flash), percent(10));

			assertEquals(0L, result.promotionDiscount());
			assertEquals(15_000_000L, result.subtotal(), "Subtotal tính theo giá flash");
		}

		@Test
		@DisplayName("Dòng flash không chặn promotion của dòng khác")
		void dongFlash_khongChanDongKhac() {
			Line flash = new Line("prod-1", "cat-1", "ASUS", 20_000_000L, 1, 15_000_000L);
			Line thuong = new Line("prod-2", "cat-1", "ASUS", 20_000_000L, 1);

			Result result = resolve(List.of(flash, thuong), percent(10));
			assertEquals(2_000_000L, result.promotionDiscount(), "Chỉ dòng thường được giảm");
			assertEquals(35_000_000L, result.subtotal());
		}

		@Test
		@DisplayName("Dòng flash vẫn vào subtotal cho điều kiện minOrderValue")
		void dongFlash_vanVaoSubtotal() {
			Promotion p = percent(10);
			p.setMinOrderValue(30_000_000L);
			Line flash = new Line("prod-1", "cat-1", "ASUS", 20_000_000L, 1, 15_000_000L);
			Line thuong = new Line("prod-2", "cat-1", "ASUS", 20_000_000L, 1);

			Result result = resolve(List.of(flash, thuong), p);
			assertEquals(35_000_000L, result.subtotal());
			assertEquals(2_000_000L, result.promotionDiscount());
		}
	}

	// ==================================================================
	// Nhiều dòng, nhiều promotion — kiểm tra tính kết hợp
	// ==================================================================

	@Nested
	@DisplayName("Kết hợp nhiều dòng và nhiều promotion")
	class KetHop {

		@Test
		@DisplayName("Mỗi dòng chọn promotion tốt nhất riêng của nó")
		void moiDong_chonPromotionRieng() {
			Promotion choLaptop = percent(10);
			PromotionScope scope = new PromotionScope();
			scope.setPromotion(choLaptop);
			scope.setTargetType(ScopeType.CATEGORY);
			scope.setTargetValue("laptop");
			choLaptop.getScopes().add(scope);

			Promotion choPhuKien = percent(20);
			PromotionScope scope2 = new PromotionScope();
			scope2.setPromotion(choPhuKien);
			scope2.setTargetType(ScopeType.CATEGORY);
			scope2.setTargetValue("phukien");
			choPhuKien.getScopes().add(scope2);

			Line laptop = new Line("prod-1", "laptop", "ASUS", 20_000_000L, 1);
			Line phukien = new Line("prod-2", "phukien", "Logitech", 1_000_000L, 1);

			Result result = resolve(List.of(laptop, phukien), choLaptop, choPhuKien);
			// 10% của 20tr = 2tr, 20% của 1tr = 200k
			assertEquals(2_200_000L, result.promotionDiscount());
			assertEquals(2, result.appliedPromotions().size());
		}

		@Test
		@DisplayName("Số lượng lớn không tràn số — dùng long")
		void soLuongLon_khongTranSo() {
			Line nhieu = new Line("prod-1", "cat-1", "ASUS", 50_000_000L, 1000);
			Result result = resolve(List.of(nhieu), percent(10));
			assertEquals(5_000_000_000L, result.promotionDiscount());
		}

		@Test
		@DisplayName("Kết quả trả về giữ nguyên thứ tự dòng đầu vào")
		void giuNguyenThuTuDong() {
			Line a = new Line("prod-a", "cat-1", "ASUS", 1_000_000L, 1);
			Line b = new Line("prod-b", "cat-1", "ASUS", 2_000_000L, 1);
			Line c = new Line("prod-c", "cat-1", "ASUS", 3_000_000L, 1);

			Result result = resolve(List.of(a, b, c), percent(10));
			List<String> ids = new ArrayList<>();
			for (PromotionEngine.LineResult lr : result.lines()) {
				ids.add(lr.productId());
			}
			assertEquals(List.of("prod-a", "prod-b", "prod-c"), ids);
		}
	}
}
