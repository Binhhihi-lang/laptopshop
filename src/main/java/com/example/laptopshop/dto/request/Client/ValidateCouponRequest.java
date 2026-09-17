package com.example.laptopshop.dto.request.Client;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Body cho POST /api/v1/client/coupons/validate.
 *
 * FE cần hỏi BE số tiền được giảm NGAY Ở TRANG GIỎ (trước khi đặt hàng) để
 * hiển thị đúng dòng "Giảm giá" trong tóm tắt đơn — không tự tính ở FE vì
 * logic ưu tiên discountAmount/discountPercent nằm ở BE.
 */
@Getter
@Setter
public class ValidateCouponRequest {

    @NotBlank(message = "COUPON_CODE_REQUIRED")
    private String code;

    // Tổng tiền hàng (chưa trừ giảm giá, chưa cộng phí ship) để tính mức giảm.
    @NotNull(message = "INVALID_ORDER_TOTAL")
    @Min(value = 0, message = "INVALID_ORDER_TOTAL")
    private Long orderTotal;
}
