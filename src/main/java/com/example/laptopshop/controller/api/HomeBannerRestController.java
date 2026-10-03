package com.example.laptopshop.controller.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.dto.request.HomeBanner.HomeBannerCreationRequest;
import com.example.laptopshop.dto.request.HomeBanner.HomeBannerStatusRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.HomeBanner.HomeBannerResponse;
import com.example.laptopshop.service.HomeBannerService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/** Admin: quản lý slide carousel trang chủ. */
@RestController
@RequestMapping("/api/v1/admin/home-banners")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HomeBannerRestController {

    HomeBannerService homeBannerService;

    @GetMapping
    @PreAuthorize("hasAuthority('READ_HOME_BANNER')")
    public ApiResponse<List<HomeBannerResponse>> getAll() {
        return ApiResponse.<List<HomeBannerResponse>>builder().result(homeBannerService.getAllBanners()).build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_HOME_BANNER')")
    public ApiResponse<HomeBannerResponse> getById(@PathVariable String id) {
        return ApiResponse.<HomeBannerResponse>builder().result(homeBannerService.getBannerById(id)).build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_HOME_BANNER')")
    public ApiResponse<HomeBannerResponse> create(
            @Valid @RequestPart("bannerInfo") HomeBannerCreationRequest request,
            @RequestPart(value = "inputFile", required = false) MultipartFile inputFile) {
        return ApiResponse.<HomeBannerResponse>builder()
                .result(homeBannerService.create(request, inputFile)).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE_HOME_BANNER')")
    public ApiResponse<HomeBannerResponse> update(@PathVariable String id,
            @Valid @RequestPart("bannerInfo") HomeBannerCreationRequest request,
            @RequestPart(value = "inputFile", required = false) MultipartFile inputFile) {
        return ApiResponse.<HomeBannerResponse>builder()
                .result(homeBannerService.update(id, request, inputFile)).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_HOME_BANNER')")
    public ApiResponse<Void> delete(@PathVariable String id) {
        homeBannerService.deleteBanner(id);
        return ApiResponse.<Void>builder().build();
    }

    /** Bật/tắt slide ngay trên thẻ ở màn danh sách. */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('UPDATE_HOME_BANNER')")
    public ApiResponse<HomeBannerResponse> updateStatus(@PathVariable String id,
            @Valid @RequestBody HomeBannerStatusRequest request) {
        return ApiResponse.<HomeBannerResponse>builder()
                .result(homeBannerService.setActive(id, request.isActive())).build();
    }
}
