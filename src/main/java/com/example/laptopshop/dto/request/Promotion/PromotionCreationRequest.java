package com.example.laptopshop.dto.request.Promotion;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import com.example.laptopshop.domain.PromotionDiscountType;
import com.example.laptopshop.domain.PromotionType;
import com.example.laptopshop.domain.ScopeType;

import lombok.Getter;
import lombok.Setter;

/**
 * DTO tạo chương trình khuyến mại (admin/staff).
 *
 * <p>
 * {@code scopeType} + {@code scopeValues} là cách diễn đạt phạm vi ở tầng API;
 * service sẽ bung ra thành danh sách {@code PromotionScope}.
 */
@Getter
@Setter
public class PromotionCreationRequest {

    @NotBlank(message = "Tên chương trình không được để trống")
    private String name;

    private String title;

    private String description;

    @NotNull(message = "Loại chương trình không được để trống")
    private PromotionType type;

    @NotNull(message = "Hình thức giảm giá không được để trống")
    private PromotionDiscountType discountType;

    /** PERCENT: 1..100. AMOUNT/FIXED_PRICE: số tiền (VND). */
    @NotNull(message = "Giá trị giảm không được để trống")
    @Min(value = 1, message = "Giá trị giảm phải lớn hơn 0")
    private Long discountValue;

    /** Trần giảm cho PERCENT. null = không giới hạn. */
    private Long maxDiscountAmount;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private LocalDateTime startDate;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    private LocalDateTime endDate;

    private Boolean active;

    /** Càng lớn càng ưu tiên khi nhiều chương trình cùng khớp. */
    private Integer priority;

    private Long minOrderValue;

    private Integer minQuantity;

    private Integer usageLimit;

    private Boolean stackable;

    @NotNull(message = "Phạm vi áp dụng không được để trống")
    private ScopeType scopeType;

    /** Category.id | Product.id | BRAND name tuỳ theo scopeType. */
    private List<String> scopeValues = new ArrayList<>();

    /** Product.id bị loại trừ khỏi chương trình. */
    private List<String> excludeProductIds = new ArrayList<>();
}
