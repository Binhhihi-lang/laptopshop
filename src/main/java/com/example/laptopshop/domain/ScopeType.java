package com.example.laptopshop.domain;

/**
 * Phạm vi áp dụng của voucher (Sprint 1 mở rộng).
 *
 * <p>
 * Voucher cũ chỉ giảm trên tổng đơn ({@link #ALL}). v1 bổ sung scope hẹp hơn để
 * giảm đúng trên phần tiền của nhóm sản phẩm khớp (D7: discount luôn tính trên
 * phần còn lại sau promotion).
 */
public enum ScopeType {
    /** Toàn bộ đơn — mặc định, tương thích voucher cũ. */
    ALL,
    /** Theo danh mục — {@code scopeValue} = Category.id. */
    CATEGORY,
    /** Theo hãng — {@code scopeValue} = Product.factory (so khớp không phân biệt hoa/thường). */
    BRAND,
    /** Theo đúng 1 sản phẩm — {@code scopeValue} = Product.id. */
    PRODUCT
}
