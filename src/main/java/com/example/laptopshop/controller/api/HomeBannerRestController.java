package com.example.laptopshop.controller.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.HomeBanner.HomeBannerCreationRequest;
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
    public ApiResponse<HomeBannerResponse> create(@Valid @ModelAttribute HomeBannerCreationRequest request) {
        return ApiResponse.<HomeBannerResponse>builder()
                .result(homeBannerService.create(request, request.getInputFile())).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE_HOME_BANNER')")
    public ApiResponse<HomeBannerResponse> update(@PathVariable String id,
            @Valid @ModelAttribute HomeBannerCreationRequest request) {
        return ApiResponse.<HomeBannerResponse>builder()
                .result(homeBannerService.update(id, request, request.getInputFile())).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_HOME_BANNER')")
    public ApiResponse<Void> delete(@PathVariable String id) {
        homeBannerService.deleteBanner(id);
        return ApiResponse.<Void>builder().build();
    }
}
