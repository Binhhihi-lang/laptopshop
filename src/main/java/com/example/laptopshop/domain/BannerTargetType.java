package com.example.laptopshop.domain;

/** Nơi một HomeBanner dẫn khách tới — FE tự build routerLink từ cặp này. */
public enum BannerTargetType {
    /** Product.id */
    PRODUCT,
    /** Category.id */
    CATEGORY,
    /** Product.factory, vd "ASUS" (D18). */
    BRAND,
    /** FlashSale.id */
    FLASH_SALE,
    /** Đường dẫn nội bộ "/..."; cấm javascript: (D30). */
    URL
}
