package com.example.laptopshop.dto.request.Promotion;

/**
 * DTO cập nhật chương trình khuyến mại.
 *
 * <p>
 * Cùng shape với {@link PromotionCreationRequest} — admin sửa toàn bộ chương
 * trình rồi gửi lên, không patch từng field. Kế thừa để tránh lệch field giữa
 * hai DTO.
 */
public class PromotionUpdateRequest extends PromotionCreationRequest {
}
