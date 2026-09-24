package com.example.laptopshop.dto.response.Promotion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.laptopshop.domain.PromotionDiscountType;
import com.example.laptopshop.domain.PromotionType;
import com.example.laptopshop.domain.ScopeType;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/** DTO trả về cho admin/staff — gồm cả scope và exclude để FE render form sửa. */
@Getter
@Setter
@Builder
public class PromotionResponse {

    private String id;

    private String name;

    private String title;

    private String description;

    private PromotionType type;

    private PromotionDiscountType discountType;

    private Long discountValue;

    private Long maxDiscountAmount;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private boolean active;

    private Integer priority;

    private Long minOrderValue;

    private Integer minQuantity;

    private Integer usageLimit;

    private int usedCount;

    private boolean stackable;

    private ScopeType scopeType;

    private List<String> scopeValues;

    private List<String> excludeProductIds;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Getter
    @Setter
    @Builder
    public static class ScopeItem {
        private ScopeType targetType;
        private String targetValue;
    }

    @Getter
    @Setter
    @Builder
    public static class ExcludeItem {
        private String productId;
    }
}
