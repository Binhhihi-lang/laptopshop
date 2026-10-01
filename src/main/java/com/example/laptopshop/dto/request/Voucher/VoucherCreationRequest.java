package com.example.laptopshop.dto.request.Voucher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.VoucherType;
import com.example.laptopshop.domain.ScopeType;

@Getter
@Setter
public class VoucherCreationRequest {

    @NotBlank(message = "VOUCHER_CODE_REQUIRED")
    private String code;

    /** Tiêu đề hiển thị cho khách trên overlay giỏ hàng. */
    private String title;

    /** Mô tả / điều kiện hiển thị cho khách. */
    private String description;

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
    /** Nhiều giá trị phạm vi: Category.id | factory | Product.id. Rỗng = toàn bộ đơn. */
    private List<String> scopeValues = new ArrayList<>();
    private VoucherType voucherType; // PUBLIC | ASSIGNED

    private boolean active = true;
    private MultipartFile inputFile;
    private String imageUrl; // URL ảnh online (thay cho inputFile khi admin dán link)

}
