package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Coupon.CouponCreationRequest;
import com.example.laptopshop.dto.request.Coupon.CouponUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Coupon.CouponResponse;
import com.example.laptopshop.service.CouponService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin")
public class CouponRestController {

    private final CouponService couponService;

    public CouponRestController(CouponService couponService) {
        this.couponService = couponService;
    }

    @GetMapping("/coupons")
    public ApiResponse<List<CouponResponse>> getAllCoupons() {
        List<CouponResponse> coupons = this.couponService.getAllCoupons();
        ApiResponse<List<CouponResponse>> response = new ApiResponse<>();
        response.setResult(coupons);
        return response;
    }

    @GetMapping("/coupons/{id}")
    public ApiResponse<CouponResponse> getCouponById(@PathVariable String id) {
        CouponResponse coupon = this.couponService.getCouponResponseById(id);
        ApiResponse<CouponResponse> response = new ApiResponse<>();
        response.setResult(coupon);
        return response;
    }

    // Coupon không có ảnh nên vẫn nhận JSON thuần qua @RequestBody, chỉ đổi từ
    // hứng trực tiếp Entity Coupon sang DTO CouponCreationRequest.
    // test postman = raw json
    @PostMapping("/coupons")
    public ApiResponse<CouponResponse> createCoupon(@Valid @RequestBody CouponCreationRequest request) {
        CouponResponse created = this.couponService.createCoupon(request);
        ApiResponse<CouponResponse> response = new ApiResponse<>();
        response.setResult(created);
        return response;
    }

    // Sửa lại vị trí @Valid: đặt trên @PathVariable long id không có tác dụng gì
    // (kiểu long không có gì để validate), @Valid phải nằm ở @RequestBody mới
    // kích hoạt @NotBlank/@NotNull bên trong CouponUpdateRequest.
    @PutMapping("/coupons/{id}")
    public ApiResponse<CouponResponse> updateCoupon(@PathVariable String id,
            @Valid @RequestBody CouponUpdateRequest request) {
        CouponResponse updated = this.couponService.updateCoupon(id, request);
        ApiResponse<CouponResponse> response = new ApiResponse<>();
        response.setResult(updated);
        return response;
    }

    @DeleteMapping("/coupons/{id}")
    public ApiResponse<Void> deleteCoupon(@PathVariable String id) {
        this.couponService.deleteCoupon(id);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setResult(null);
        return response;
    }

}
