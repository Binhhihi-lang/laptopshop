package com.example.laptopshop.dto.request.HomeBanner;

import lombok.Getter;

/** Bật/tắt một slide banner (switch trên thẻ ở màn danh sách). */
@Getter
public class HomeBannerStatusRequest {

    private boolean active;

}
