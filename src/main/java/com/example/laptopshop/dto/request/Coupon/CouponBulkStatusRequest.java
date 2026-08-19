package com.example.laptopshop.dto.request.Coupon;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;

@Getter
public class CouponBulkStatusRequest {

    @NotEmpty(message = "INVALID_COUPON_DATA")
    private List<String> ids;

    private boolean active;

}
