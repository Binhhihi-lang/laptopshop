package com.example.laptopshop.domain;

/**
 * Nghiệp vụ mà chương trình khuyến mại thực hiện. Tách khỏi
 * {@link PromotionDiscountType} vì "làm gì" và "giảm bao nhiêu" là hai câu hỏi
 * khác nhau: một {@link #PRODUCT_DISCOUNT} có thể giảm theo % hoặc theo tiền.
 */
public enum PromotionType {
    /** Giảm giá trên từng sản phẩm khớp phạm vi — loại duy nhất Sprint 2 tính tiền. */
    PRODUCT_DISCOUNT,
    /** Hoàn voucher vào ví khách khi đơn hoàn tất — để Sprint 5. */
    GIFT_VOUCHER,
    /** Mua kèm / bundle — để Sprint 5. */
    BUNDLE
}
