package com.example.laptopshop.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Promotion;
import com.example.laptopshop.domain.PromotionExclude;
import com.example.laptopshop.domain.PromotionScope;
import com.example.laptopshop.domain.ScopeType;
import com.example.laptopshop.dto.request.Promotion.PromotionCreationRequest;
import com.example.laptopshop.dto.request.Promotion.PromotionUpdateRequest;
import com.example.laptopshop.dto.response.Promotion.PromotionResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.PromotionRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * CRUD chương trình khuyến mại (admin/staff).
 *
 * <p>
 * Chỉ quản lý vòng đời chương trình. Việc tính giảm giá nằm ở
 * {@link PromotionEngine} — service này không quyết định giá.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionService {

    PromotionRepository promotionRepository;

    @Transactional(readOnly = true)
    public List<PromotionResponse> getAll() {
        return promotionRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public PromotionResponse getById(String id) {
        return toResponse(findOrThrow(id));
    }

    /**
     * Ứng viên promotion cho giỏ hiện tại — lọc thô ở DB rồi để
     * {@link PromotionEngine} quyết định chi tiết (§3.2).
     *
     * <p>
     * Lọc thô gồm: còn hoạt động, chưa xoá mềm, còn ngân sách. Điều kiện theo
     * dòng (scope, minQuantity, exclude) do engine xét vì cần biết từng sản phẩm.
     *
     * @param now mốc thời gian xét hiệu lực — tham số hoá để test tất định
     */
    @Transactional(readOnly = true)
    public List<Promotion> findApplicable(LocalDateTime now) {
        return promotionRepository.findActiveAt(now).stream().filter(p -> p.getDeletedAt() == null).toList();
    }

    @Transactional
    public PromotionResponse create(PromotionCreationRequest request) {
        validate(request);
        Promotion promotion = new Promotion();
        applyRequest(promotion, request);
        return toResponse(promotionRepository.save(promotion));
    }

    @Transactional
    public PromotionResponse update(String id, PromotionUpdateRequest request) {
        validate(request);
        Promotion promotion = findOrThrow(id);
        applyRequest(promotion, request);
        return toResponse(promotionRepository.save(promotion));
    }

    /**
     * Ngừng áp chương trình. Không xóa cứng vì đơn cũ còn tham chiếu tới
     * promotion đã áp (D22) — xóa sẽ làm hỏng lịch sử đơn hàng.
     */
    @Transactional
    public void deactivate(String id) {
        Promotion promotion = findOrThrow(id);
        promotion.setActive(false);
        promotionRepository.save(promotion);
    }

    private Promotion findOrThrow(String id) {
        return promotionRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.PROMOTION_NOT_FOUND));
    }

    /**
     * Kiểm tra cấu hình trước khi lưu.
     *
     * <p>
     * Chặn ở tầng service chứ không chỉ dựa vào {@code @NotNull} của DTO: admin có
     * thể gọi API trực tiếp, và các ràng buộc XOR (percent vs amount) không diễn
     * đạt được bằng annotation đơn giản.
     */
    private void validate(PromotionCreationRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new AppException(ErrorCode.PROMOTION_NAME_REQUIRED);
        }
        validateTimeRange(request);
        validateDiscountValue(request);
        validateScope(request);
    }

    private void validateDiscountValue(PromotionCreationRequest request) {
        if (request.getDiscountType() == null || request.getDiscountValue() == null) {
            throw new AppException(ErrorCode.INVALID_PROMOTION_CONFIG);
        }
        switch (request.getDiscountType()) {
            case PERCENT -> {
                if (request.getDiscountValue() < 1 || request.getDiscountValue() > 100) {
                    throw new AppException(ErrorCode.INVALID_PROMOTION_PERCENT);
                }
            }
            case AMOUNT, FIXED_PRICE, QUANTITY_TIER -> {
                if (request.getDiscountValue() <= 0) {
                    throw new AppException(ErrorCode.INVALID_PROMOTION_AMOUNT);
                }
            }
        }
    }

    /**
     * {@code scopeType = ALL} không cần giá trị; mọi loại khác phải có ít nhất một
     * giá trị, nếu không chương trình sẽ không bao giờ khớp sản phẩm nào.
     */
    private void validateScope(PromotionCreationRequest request) {
        if (request.getScopeType() == null || request.getScopeType() == ScopeType.ALL) {
            return;
        }
        boolean empty = request.getScopeValues() == null
                || request.getScopeValues().stream().allMatch(v -> v == null || v.isBlank());
        if (empty) {
            throw new AppException(ErrorCode.PROMOTION_SCOPE_REQUIRED);
        }
    }

    private void validateTimeRange(PromotionCreationRequest request) {
        if (request.getStartDate() != null && request.getEndDate() != null
                && !request.getEndDate().isAfter(request.getStartDate())) {
            throw new AppException(ErrorCode.INVALID_PROMOTION_DATE_RANGE);
        }
    }

    /**
     * Đổ dữ liệu từ request vào entity.
     *
     * <p>
     * Scope và exclude được dựng lại từ đầu mỗi lần lưu: admin gửi lên danh sách
     * đầy đủ, nên thay thế toàn bộ đơn giản và đúng hơn là so khớp từng phần tử.
     */
    private void applyRequest(Promotion promotion, PromotionCreationRequest request) {
        promotion.setName(request.getName().trim());
        promotion.setTitle(request.getTitle());
        promotion.setDescription(request.getDescription());
        promotion.setType(request.getType());
        promotion.setDiscountType(request.getDiscountType());
        promotion.setDiscountValue(request.getDiscountValue());
        promotion.setMaxDiscountAmount(request.getMaxDiscountAmount());
        promotion.setStartDate(request.getStartDate());
        promotion.setEndDate(request.getEndDate());
        promotion.setActive(request.getActive() == null || request.getActive());
        promotion.setPriority(request.getPriority());
        promotion.setMinOrderValue(request.getMinOrderValue());
        promotion.setMinQuantity(request.getMinQuantity());
        promotion.setUsageLimit(request.getUsageLimit());
        promotion.setStackable(request.getStackable() != null && request.getStackable());

        promotion.getScopes().clear();
        if (request.getScopeType() != null && request.getScopeValues() != null) {
            for (String value : new HashSet<>(request.getScopeValues())) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                PromotionScope scope = new PromotionScope();
                scope.setPromotion(promotion);
                scope.setTargetType(request.getScopeType());
                scope.setTargetValue(PromotionScope.normalizeTargetValue(request.getScopeType(), value));
                promotion.getScopes().add(scope);
            }
        }

        promotion.getExcludes().clear();
        if (request.getExcludeProductIds() != null) {
            for (String productId : new HashSet<>(request.getExcludeProductIds())) {
                if (productId == null || productId.isBlank()) {
                    continue;
                }
                PromotionExclude exclude = new PromotionExclude();
                exclude.setPromotion(promotion);
                exclude.setProductId(productId.trim());
                promotion.getExcludes().add(exclude);
            }
        }
    }

    private PromotionResponse toResponse(Promotion promotion) {
        List<String> scopeValues = new ArrayList<>();
        ScopeType scopeType = null;
        for (PromotionScope scope : promotion.getScopes()) {
            scopeType = scope.getTargetType();
            scopeValues.add(scope.getTargetValue());
        }

        List<String> excludeIds = promotion.getExcludes().stream().map(PromotionExclude::getProductId).toList();

        return PromotionResponse.builder()
                .id(promotion.getId())
                .name(promotion.getName())
                .title(promotion.getTitle())
                .description(promotion.getDescription())
                .type(promotion.getType())
                .discountType(promotion.getDiscountType())
                .discountValue(promotion.getDiscountValue())
                .maxDiscountAmount(promotion.getMaxDiscountAmount())
                .startDate(promotion.getStartDate())
                .endDate(promotion.getEndDate())
                .active(promotion.isActive())
                .priority(promotion.getPriority())
                .minOrderValue(promotion.getMinOrderValue())
                .minQuantity(promotion.getMinQuantity())
                .usageLimit(promotion.getUsageLimit())
                .usedCount(promotion.getUsedCount())
                .stackable(promotion.isStackable())
                .scopeType(scopeType)
                .scopeValues(scopeValues)
                .excludeProductIds(excludeIds)
                .createdAt(promotion.getCreatedAt())
                .updatedAt(promotion.getUpdatedAt())
                .build();
    }
}
