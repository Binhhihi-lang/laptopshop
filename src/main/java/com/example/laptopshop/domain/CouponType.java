package com.example.laptopshop.domain;

/**
 * Loại coupon theo nguồn phát hành (Sprint 1 mở rộng).
 *
 * <p>
 * Quyết định coupon có nằm trong ví của khách hay không: {@link #PUBLIC} thì
 * khách tự nhập mã ở checkout, còn {@link #ASSIGNED}/{@link #GIFT} phải được
 * cấp vào ví trước khi dùng.
 */
public enum CouponType {
    /** Công khai — khách gõ mã ở checkout, không cần claim. */
    PUBLIC,
    /** Admin gán tay cho 1 khách cụ thể. */
    ASSIGNED,
    /** Tặng tự động (ví dụ thưởng sau đơn) — không gõ tay được. */
    GIFT
}
