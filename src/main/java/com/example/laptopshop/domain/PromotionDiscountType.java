package com.example.laptopshop.domain;


public enum PromotionDiscountType {
    /** Giảm theo % trên thành tiền của dòng. {@code discountValue} = 1..100. */
    PERCENT,

    /**
     * Giảm một số tiền CHO MỖI MÁY rồi nhân số lượng (D21). Mua 2 máy với mức
     * 500.000đ thì giảm 1.000.000đ. Khác với {@code Voucher.discountAmount} —
     * voucher trừ MỘT LẦN cho cả đơn.
     */
    AMOUNT
}
