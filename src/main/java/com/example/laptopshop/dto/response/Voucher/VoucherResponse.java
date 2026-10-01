package com.example.laptopshop.dto.response.Voucher;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

import com.example.laptopshop.domain.VoucherType;
import com.example.laptopshop.domain.ScopeType;

@Getter
@Setter
public class VoucherResponse {

    private String id;
    private String code;
    private String title;
    private String description;
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
    /** Danh sách giá trị phạm vi (nhiều dòng). Rỗng = toàn bộ đơn. */
    private List<String> scopeValues;
    private VoucherType voucherType;

    private String image;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
