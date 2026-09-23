package com.example.laptopshop.dto.response.Coupon;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

import com.example.laptopshop.domain.CouponType;
import com.example.laptopshop.domain.ScopeType;

@Getter
@Setter
public class CouponResponse {

    private String id;
    private String code;
    private Integer discountPercent;
    private Long discountAmount;
    private LocalDateTime startDate;
    private LocalDateTime expiryDate;
    private Integer usageLimit;
    private Integer usedCount;
    private boolean active;

    // ===== v1 =====
    private Long minOrderValue;
    private Long maxDiscountAmount;
    private Integer perUserLimit;
    private ScopeType scopeType;
    private String scopeValue;
    private CouponType couponType;

    private String image;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
