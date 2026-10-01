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
 * Phạm vi áp dụng của một {@link Voucher} — voucher chỉ giảm trên tiền hàng của
 * những dòng khớp scope.
 *
 * <p>
 * Một voucher có thể có nhiều dòng scope (giảm cho cả danh mục Laptop lẫn danh
 * mục Phụ kiện). Không có dòng nào = áp cho toàn bộ đơn (ALL).
 *
 * <p>
 * Khuôn giống {@link PromotionScope}: {@link ScopeType#BRAND} so khớp với
 * {@code Product.factory} nên {@code targetValue} phải chuẩn hoá trim +
 * uppercase ngay khi lưu.
 */
@Entity
@Table(name = "voucher_scopes")
@Getter
@Setter
public class VoucherScope {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScopeType targetType;

    /** {@code Category.id} | {@code Product.factory} | {@code Product.id}. */
    @Column(name = "target_value", nullable = false)
    private String targetValue;

    /** Chuẩn hoá trước khi lưu — BRAND trim + uppercase, còn lại chỉ trim. */
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
