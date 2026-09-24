package com.example.laptopshop.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Phạm vi áp dụng của một {@link Promotion} — chương trình chỉ giảm trên những
 * dòng sản phẩm khớp scope.
 *
 * <p>
 * Một promotion có thể có nhiều dòng scope (giảm cho cả danh mục Laptop lẫn
 * danh mục Phụ kiện). Không có dòng scope nào = áp cho toàn bộ đơn (ALL), giữ
 * tương thích với cách hiểu cũ.
 *
 * <p>
 * <b>D18:</b> {@link ScopeType#BRAND} so khớp với {@code Product.factory} chứ
 * không có bảng Brand riêng, nên {@code targetValue} phải được normalize
 * trim + uppercase ngay khi lưu (xem {@link #normalizeTargetValue}).
 */
@Entity
@Table(name = "promotion_scopes")
@Getter
@Setter
public class PromotionScope {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private String id;

	@ManyToOne
	@JoinColumn(name = "promotion_id", nullable = false)
	private Promotion promotion;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private ScopeType targetType;

	/**
	 * Định danh phạm vi: {@code Category.id} | {@code Product.factory} |
	 * {@code Product.id}. Với {@link ScopeType#ALL} giá trị này bị bỏ qua.
	 */
	@Column(name = "target_value", nullable = false)
	private String targetValue;

	/**
	 * Chuẩn hoá giá trị scope trước khi lưu/so khớp.
	 *
	 * <p>
	 * Chỉ {@link ScopeType#BRAND} mới cần trim + uppercase vì nó so khớp với
	 * {@code Product.factory} — dữ liệu người dùng nhập ("Asus", " asus ")
	 * phải quy về một dạng duy nhất. ID (CATEGORY/PRODUCT) là UUID nên giữ
	 * nguyên, chỉ trim cho sạch.
	 */
	public static String normalizeTargetValue(ScopeType targetType, String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.trim();
		if (targetType == ScopeType.BRAND) {
			return trimmed.toUpperCase();
		}
		return trimmed;
	}
}
