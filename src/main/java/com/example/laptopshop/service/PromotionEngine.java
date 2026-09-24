package com.example.laptopshop.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.laptopshop.domain.Promotion;
import com.example.laptopshop.domain.PromotionDiscountType;
import com.example.laptopshop.domain.PromotionExclude;
import com.example.laptopshop.domain.PromotionScope;
import com.example.laptopshop.domain.ScopeType;

/**
 * Engine tính khuyến mại — HÀM THUẦN, không chạm DB.
 *
 * <p>
 * Vì thuần nên mọi điều kiện (thời gian, lượt dùng, scope) đều xét trên danh
 * sách {@code promotions} truyền vào — nơi gọi lọc thô từ DB trước. Nhờ vậy
 * test được toàn bộ ma trận quyết định mà không cần Spring context.
 *
 * <p>
 * Quy tắc tiền (L1): mọi con số là {@code long}, đơn vị đồng. Không dùng
 * {@code double} ở bất kỳ bước nào để tránh sai số làm tròn.
 *
 * <p>
 * Thứ tự cứng (D9): promotion chạy trước trên từng dòng → voucher tính sau
 * trên {@code subtotal − promotionDiscount}. Engine này chỉ lo phần promotion.
 */
@Service
public class PromotionEngine {

	/**
	 * Một dòng hàng đưa vào engine — tách khỏi {@code CartItem} để test không
	 * cần entity.
	 *
	 * @param flashPrice giá flash nếu dòng đang trong phiên flash sale; khác
	 *                   {@code null} thì flash thắng và promotion bị bỏ qua
	 *                   cho dòng đó (D25)
	 */
	public record Line(String productId, String categoryId, String factory, long unitPrice, int quantity,
			Long flashPrice) {

		public Line(String productId, String categoryId, String factory, long unitPrice, int quantity) {
			this(productId, categoryId, factory, unitPrice, quantity, null);
		}

		/** Giá dùng để tính tiền: flash thắng giá gốc khi có phiên (D25). */
		public long effectiveUnitPrice() {
			return flashPrice != null ? flashPrice : unitPrice;
		}

		public long lineTotal() {
			return effectiveUnitPrice() * quantity;
		}

		public boolean hasFlash() {
			return flashPrice != null;
		}
	}

	/** Kết quả giảm của một dòng. */
	public record LineResult(String productId, long lineTotal, long discount, Promotion promotion) {
	}

	/** Kết quả toàn đơn. */
	public record Result(long subtotal, long promotionDiscount, List<LineResult> lines) {

		/** Promotion đã áp, không trùng — FE hiện ở overlay (D14). */
		public List<Promotion> appliedPromotions() {
			Set<Promotion> distinct = new LinkedHashSet<>();
			for (LineResult line : lines) {
				if (line.promotion() != null) {
					distinct.add(line.promotion());
				}
			}
			return new ArrayList<>(distinct);
		}
	}

	/**
	 * Tính promotion cho toàn giỏ.
	 *
	 * @param lines      các dòng hàng; rỗng → kết quả rỗng, không ném lỗi
	 * @param promotions ứng viên đã lọc thô từ DB
	 * @param now        mốc xét hiệu lực — tham số hoá để test tất định
	 */
	public Result resolve(List<Line> lines, List<Promotion> promotions, LocalDateTime now) {
		if (lines == null || lines.isEmpty()) {
			return new Result(0L, 0L, List.of());
		}

		List<Promotion> candidates = promotions == null ? List.of() : promotions;
		long subtotal = 0L;
		for (Line line : lines) {
			subtotal += line.lineTotal();
		}

		List<LineResult> results = new ArrayList<>(lines.size());
		// D5: mỗi dòng chỉ 1 promotion thắng. Gom theo promotion để cap
		// maxDiscountAmount trên TỔNG các dòng nó thắng (D23), không cap lẻ.
		Map<String, Long> rawByPromotion = new LinkedHashMap<>();
		Map<String, Promotion> promotionById = new LinkedHashMap<>();

		for (Line line : lines) {
			// D25: dòng đã có giá flash thì flash là giá sâu nhất — bỏ promotion.
			Promotion winner = line.hasFlash() ? null : pickWinner(line, candidates, subtotal, now);
			if (winner == null) {
				results.add(new LineResult(line.productId(), line.lineTotal(), 0L, null));
				continue;
			}
			long discount = Math.min(rawDiscount(winner, line), line.lineTotal());
			rawByPromotion.merge(winner.getId(), discount, Long::sum);
			promotionById.put(winner.getId(), winner);
			results.add(new LineResult(line.productId(), line.lineTotal(), discount, winner));
		}

		// Cap theo TỪNG promotion trên tổng các dòng nó thắng (D23).
		Map<String, Long> remainingExcess = new LinkedHashMap<>();
		for (Map.Entry<String, Long> entry : rawByPromotion.entrySet()) {
			Promotion p = promotionById.get(entry.getKey());
			long sum = entry.getValue();
			Long cap = p.getMaxDiscountAmount();
			if (cap != null && sum > cap) {
				remainingExcess.put(entry.getKey(), sum - Math.max(0L, cap));
			}
		}

		if (!remainingExcess.isEmpty()) {
			List<LineResult> trimmed = new ArrayList<>(results.size());
			for (LineResult line : results) {
				long discount = line.discount();
				if (line.promotion() != null) {
					Long excess = remainingExcess.get(line.promotion().getId());
					if (excess != null && excess > 0L) {
						long cut = Math.min(excess, discount);
						discount -= cut;
						remainingExcess.put(line.promotion().getId(), excess - cut);
					}
				}
				trimmed.add(new LineResult(line.productId(), line.lineTotal(), discount, line.promotion()));
			}
			results = trimmed;
		}

		long promotionDiscount = 0L;
		for (LineResult line : results) {
			promotionDiscount += line.discount();
		}
		// D10: tổng giảm không bao giờ vượt subtotal.
		promotionDiscount = Math.min(promotionDiscount, subtotal);

		return new Result(subtotal, promotionDiscount, List.copyOf(results));
	}

	/** Chọn promotion thắng cho một dòng: priority cao thắng, hoà thì giảm nhiều hơn (D5). */
	private Promotion pickWinner(Line line, List<Promotion> candidates, long subtotal, LocalDateTime now) {
		Promotion winner = null;
		long bestDiscount = 0L;
		for (Promotion p : candidates) {
			if (!matches(p, line, subtotal, now)) {
				continue;
			}
			long raw = rawDiscount(p, line);
			if (raw <= 0L) {
				continue;
			}
			long capped = Math.min(raw, line.lineTotal());
			if (capped <= 0L) {
				continue;
			}
			if (winner == null
					|| priorityOf(p) > priorityOf(winner)
					|| (priorityOf(p) == priorityOf(winner) && capped > bestDiscount)) {
				winner = p;
				bestDiscount = capped;
			}
		}
		return winner;
	}

	/** Điều kiện xét một promotion trên một dòng, theo thứ tự §3.2. */
	private boolean matches(Promotion p, Line line, long subtotal, LocalDateTime now) {
		if (!p.isActive()) {
			return false;
		}
		if (now != null && !p.isWithinPeriod(now)) {
			return false;
		}
		// usageLimit đếm theo ĐƠN đã áp, không theo dòng.
		if (!p.hasBudget()) {
			return false;
		}
		if (p.getMinQuantity() != null && line.quantity() < p.getMinQuantity()) {
			return false;
		}
		if (p.getMinOrderValue() != null && subtotal < p.getMinOrderValue()) {
			return false;
		}
		if (!matchesScope(p, line)) {
			return false;
		}
		return !inExcludeList(p, line);
	}

	/**
	 * D17: {@code ALL} (hoặc không có scope) → giảm cả giỏ; ngược lại chỉ giảm
	 * trên dòng khớp scope.
	 */
	private boolean matchesScope(Promotion p, Line line) {
		List<PromotionScope> scopes = p.getScopes();
		if (scopes == null || scopes.isEmpty()) {
			return true;
		}
		for (PromotionScope scope : scopes) {
			ScopeType type = scope.getTargetType();
			if (type == null || type == ScopeType.ALL) {
				return true;
			}
			String target = scope.getTargetValue();
			if (target == null) {
				continue;
			}
			if (type == ScopeType.CATEGORY && target.equals(line.categoryId())) {
				return true;
			}
			// D18: BRAND so với Product.factory; lưu đã uppercase nên so 2 chiều.
			if (type == ScopeType.BRAND && line.factory() != null && target.equalsIgnoreCase(line.factory())) {
				return true;
			}
			if (type == ScopeType.PRODUCT && target.equals(line.productId())) {
				return true;
			}
		}
		return false;
	}

	/** Loại trừ thắng scope: sản phẩm trong danh sách này không bao giờ được giảm. */
	private boolean inExcludeList(Promotion p, Line line) {
		List<PromotionExclude> excludes = p.getExcludes();
		if (excludes == null || excludes.isEmpty()) {
			return false;
		}
		for (PromotionExclude exclude : excludes) {
			if (line.productId() != null && line.productId().equals(exclude.getProductId())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * D21 — hai cấp khác nhau, cố ý: PERCENT tính trên tổng dòng, còn
	 * AMOUNT/FIXED_PRICE tính trên MỖI MÁY rồi nhân số lượng.
	 */
	private long rawDiscount(Promotion p, Line line) {
		PromotionDiscountType type = p.getDiscountType();
		Long value = p.getDiscountValue();
		if (type == null || value == null || value <= 0L) {
			return 0L;
		}
		long lineTotal = line.lineTotal();
		return switch (type) {
			case PERCENT -> {
				long pct = Math.min(value, 100L);
				yield lineTotal * pct / 100L;
			}
			case AMOUNT -> value * line.quantity();
			case FIXED_PRICE -> {
				long perUnit = line.effectiveUnitPrice() - value;
				yield perUnit <= 0L ? 0L : perUnit * line.quantity();
			}
			// Chưa mở ở v1 — xem PromotionDiscountType.
			case QUANTITY_TIER -> 0L;
		};
	}

	private int priorityOf(Promotion p) {
		return p.getPriority() == null ? 0 : p.getPriority();
	}
}
