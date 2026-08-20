package com.example.laptopshop.controller.api;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Coupon.CouponCreationRequest;
import com.example.laptopshop.dto.request.Coupon.CouponUpdateRequest;
import com.example.laptopshop.dto.request.Coupon.CouponBulkDeleteRequest;
import com.example.laptopshop.dto.request.Coupon.CouponBulkStatusRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Coupon.CouponResponse;
import com.example.laptopshop.service.CouponService;

import jakarta.validation.Valid;
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/admin")
public class CouponRestController {

    CouponService couponService;

    @GetMapping("/coupons")
    @PreAuthorize("hasAuthority('READ_COUPON')")
    public ApiResponse<List<CouponResponse>> getAllCoupons() {
        List<CouponResponse> coupons = this.couponService.getAllCoupons();
        ApiResponse<List<CouponResponse>> response = new ApiResponse<>();
        response.setResult(coupons);
        return response;
    }

    @GetMapping("/coupons/{id}")
    @PreAuthorize("hasAuthority('READ_COUPON')")
    public ApiResponse<CouponResponse> getCouponById(@PathVariable String id) {
        CouponResponse coupon = this.couponService.getCouponResponseById(id);
        ApiResponse<CouponResponse> response = new ApiResponse<>();
        response.setResult(coupon);
        return response;
    }

    // Coupon có ảnh nên nhận dữ liệu dạng form-data qua @ModelAttribute (giống
    // Category/Product) để hỗ trợ upload ảnh. Toàn bộ map/validate/xử lý ảnh nằm ở Service.
    @PostMapping("/coupons")
    @PreAuthorize("hasAuthority('CREATE_COUPON')")
    public ApiResponse<CouponResponse> createCoupon(@Valid @ModelAttribute CouponCreationRequest request) {
        CouponResponse created = this.couponService.createCoupon(request);
        ApiResponse<CouponResponse> response = new ApiResponse<>();
        response.setResult(created);
        return response;
    }

    @PutMapping("/coupons/{id}")
    @PreAuthorize("hasAuthority('UPDATE_COUPON')")
    public ApiResponse<CouponResponse> updateCoupon(@PathVariable String id,
            @Valid @ModelAttribute CouponUpdateRequest request) {
        CouponResponse updated = this.couponService.updateCoupon(id, request);
        ApiResponse<CouponResponse> response = new ApiResponse<>();
        response.setResult(updated);
        return response;
    }

    @DeleteMapping("/coupons/{id}")
    @PreAuthorize("hasAuthority('DELETE_COUPON')")
    public ApiResponse<Void> deleteCoupon(@PathVariable String id) {
        this.couponService.deleteCoupon(id);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setResult(null);
        return response;
    }

    // Xóa hàng loạt coupon theo danh sách id (body JSON { ids: [...] }).
    // Xóa ảnh + xóa mềm nằm trong 1 transaction ở CouponService.deleteCouponsByIds().
    @PostMapping("/coupons/bulk-delete")
    @PreAuthorize("hasAuthority('DELETE_COUPON')")
    public ApiResponse<Void> deleteCoupons(@Valid @RequestBody CouponBulkDeleteRequest request) {
        this.couponService.deleteCouponsByIds(request.getIds());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setResult(null);
        return response;
    }

    // Kích hoạt/khóa hàng loạt coupon (body JSON { ids: [...], active: true/false })
    @PatchMapping("/coupons/bulk-status")
    @PreAuthorize("hasAuthority('UPDATE_COUPON')")
    public ApiResponse<Void> updateCouponsActive(@Valid @RequestBody CouponBulkStatusRequest request) {
        this.couponService.updateCouponsActive(request.getIds(), request.isActive());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setResult(null);
        return response;
    }

}
