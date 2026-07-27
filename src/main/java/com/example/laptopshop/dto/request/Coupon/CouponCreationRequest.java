package com.example.laptopshop.dto.request.Coupon;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter

public class CouponCreationRequest {

    @NotBlank(message = "COUPON_CODE_REQUIRED")
    private String code;

    private Integer discountPercent;

    private Long discountAmount;

    private LocalDateTime expiryDate;

    private Integer usageLimit;

}
