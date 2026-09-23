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
public class CouponUpdateRequest {

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
    private Long minOrderValue;
    private Long maxDiscountAmount;
    private Integer perUserLimit;
    private ScopeType scopeType;
    private String scopeValue;
    private CouponType couponType;

    private boolean active = true;
    private MultipartFile inputFile;
    private boolean removeImage = false; // true = xóa ảnh hiện tại nếu không gửi file mới
    private String imageUrl; // URL ảnh online (thay cho inputFile khi admin dán link)

}
