package com.example.laptopshop.domain;

/**
 * Cách tính mức giảm của một khuyến mại.
 *
 * <p>
 * Hai giá trị đầu đang dùng được; hai giá trị cuối để sẵn trong enum cho đúng
 * hợp đồng API nhưng {@code PromotionService} chặn khi tạo/sửa — làm gọn vòng
 * lặp phát triển mà không phải đổi enum về sau.
 */
public enum PromotionDiscountType {
    /** Giảm theo % trên thành tiền của dòng. {@code discountValue} = 1..100. */
    PERCENT,

    /**
     * Giảm một số tiền CHO MỖI MÁY rồi nhân số lượng (D21). Mua 2 máy với mức
     * 500.000đ thì giảm 1.000.000đ. Khác với {@code Coupon.discountAmount} —
     * mã giảm giá trừ MỘT LẦN cho cả đơn.
     */
    AMOUNT,

    /**
     * Ép về một giá bán cố định cho mỗi máy (D21). Chưa mở ở v1 vì cần quy tắc
     * riêng khi giá đích cao hơn giá hiện hành.
     */
    FIXED_PRICE,

    /** Giảm theo bậc số lượng (mua ≥3 được nhiều hơn). Chưa mở ở v1. */
    QUANTITY_TIER
}
