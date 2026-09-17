package com.example.laptopshop.controller.api.client;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Client.ValidateCouponRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Client.CouponValidationResponse;
import com.example.laptopshop.service.OrderService;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Storefront: kiểm tra mã giảm giá ở trang giỏ hàng.
 * Đường dẫn: /api/v1/client/coupons
 *
 * Vì sao cần endpoint riêng thay vì để FE tự tính: logic ưu tiên
 * discountAmount / discountPercent và các điều kiện hết hạn/hết lượt nằm ở BE
 * — FE tính lại sẽ lệch số so với lúc đặt hàng thật.
 *
 * Luôn trả HTTP 200 kèm cờ {@code valid} (kể cả mã sai) để FE hiển thị thông
 * báo inline dưới ô nhập mã, thay vì ném lỗi đỏ.
 */
@RestController
@RequestMapping("/api/v1/client/coupons")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClientCouponController {

    OrderService orderService;

    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<CouponValidationResponse> validate(@Valid @RequestBody ValidateCouponRequest request) {
        ApiResponse<CouponValidationResponse> response = new ApiResponse<>();
        response.setResult(this.orderService.validateCoupon(request.getCode(), request.getOrderTotal()));
        return response;
    }
}
