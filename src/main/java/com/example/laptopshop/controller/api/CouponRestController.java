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

import com.example.laptopshop.domain.Coupon;
import com.example.laptopshop.dto.request.Coupon.CouponCreationRequest;
import com.example.laptopshop.dto.request.Coupon.CouponUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
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
    public ApiResponse<List<Coupon>> getAllCoupons() {
        ApiResponse<List<Coupon>> response = new ApiResponse<>();
        response.setResult(this.couponService.getAllCoupons());
        return response;
    }

    @GetMapping("/coupons/{id}")
    public ApiResponse<Coupon> getCouponById(@PathVariable long id) {
        ApiResponse<Coupon> response = new ApiResponse<>();
        response.setResult(this.couponService.getCouponById(id));
        return response;
    }

    // Coupon không có ảnh nên vẫn nhận JSON thuần qua @RequestBody, chỉ đổi từ
    // hứng trực tiếp Entity Coupon sang DTO CouponCreationRequest.
    // test postman = raw json
    @PostMapping("/coupons")
    public ApiResponse<Coupon> createCoupon(@Valid @RequestBody CouponCreationRequest request) {
        Coupon created = this.couponService.createCoupon(request);
        ApiResponse<Coupon> response = new ApiResponse<>();
        response.setResult(created);
        return response;
    }

    @PutMapping("/coupons/{id}")
    public ApiResponse<Coupon> updateCoupon(@Valid @PathVariable long id, @RequestBody CouponUpdateRequest request) {
        Coupon updated = this.couponService.updateCoupon(id, request);
        ApiResponse<Coupon> response = new ApiResponse<>();
        response.setResult(updated);
        return response;
    }

    @DeleteMapping("/coupons/{id}")
    public ApiResponse<Void> deleteCoupon(@PathVariable long id) {
        this.couponService.deleteCoupon(id);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setResult(null);
        return response;
    }

}