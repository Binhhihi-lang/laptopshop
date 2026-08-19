package com.example.laptopshop.dto.response.Coupon;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CouponResponse {

    private String id;
    private String code;
    private Integer discountPercent;
    private Long discountAmount;
    private LocalDateTime expiryDate;
    private Integer usageLimit;
    private Integer usedCount;
    private boolean active;
    private String image;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
