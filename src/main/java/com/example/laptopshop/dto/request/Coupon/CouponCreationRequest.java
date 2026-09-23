package com.example.laptopshop.dto.request.Coupon;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.CouponType;
import com.example.laptopshop.domain.ScopeType;

@Getter
@Setter
public class CouponCreationRequest {

    @NotBlank(message = "COUPON_CODE_REQUIRED")
    private String code;

    private Integer discountPercent;

    private Long discountAmount;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startDate; // null = hiệu lực ngay

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expiryDate;

    private Integer usageLimit;

    // ===== v1: điều kiện áp dụng (nullable — null = không giới hạn) =====
    private Long minOrderValue; // giá trị đơn tối thiểu
    private Long maxDiscountAmount; // trần giảm khi dùng discountPercent
    private Integer perUserLimit; // số lượt tối đa mỗi khách
    private ScopeType scopeType; // ALL | CATEGORY | BRAND | PRODUCT
    private String scopeValue; // Category.id | factory | Product.id
    private CouponType couponType; // PUBLIC | ASSIGNED | GIFT

    private boolean active = true;
    private MultipartFile inputFile;
    private String imageUrl; // URL ảnh online (thay cho inputFile khi admin dán link)

}
