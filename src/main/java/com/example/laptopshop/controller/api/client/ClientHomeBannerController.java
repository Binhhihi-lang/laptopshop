package com.example.laptopshop.controller.api.client;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.HomeBanner.HomeBannerResponse;
import com.example.laptopshop.service.HomeBannerService;

/**
 * Storefront: slide carousel trang chủ. Public — khách chưa login vẫn thấy home.
 * Đường dẫn: /api/v1/client/home-banners
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/client/home-banners")
public class ClientHomeBannerController {

    HomeBannerService homeBannerService;

    @GetMapping
    public ApiResponse<List<HomeBannerResponse>> getActiveBanners() {
        return ApiResponse.<List<HomeBannerResponse>>builder()
                .result(homeBannerService.getActiveBanners()).build();
    }
}
