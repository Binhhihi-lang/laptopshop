package com.example.laptopshop.domain;

/**
 * Nơi một HomeBanner dẫn khách tới — FE tự build routerLink từ cặp này.
 *
 * <p>
 * Không có loại "URL tự do": mọi đích đều là thực thể có thật trong hệ thống,
 * nên admin chọn từ danh sách thay vì gõ tay. Nhờ vậy không có đường nào cho
 * link ngoài hay {@code javascript:} lọt vào banner.
 */
public enum BannerTargetType {
    /** Product.id */
    PRODUCT,
    /** Category.id */
    CATEGORY,
    /** Product.factory, vd "ASUS". */
    BRAND,
    /** FlashSale.id */
    FLASH_SALE
}
