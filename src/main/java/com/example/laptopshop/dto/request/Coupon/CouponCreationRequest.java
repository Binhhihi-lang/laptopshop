package com.example.laptopshop.dto.request.Coupon;

import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class CouponCreationRequest {

    @NotBlank(message = "COUPON_CODE_REQUIRED")
    private String code;

    private Integer discountPercent;

    private Long discountAmount;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime expiryDate;

    private Integer usageLimit;
    private boolean active = true;
    private MultipartFile inputFile;

}
