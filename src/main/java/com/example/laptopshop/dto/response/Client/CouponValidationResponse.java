package com.example.laptopshop.dto.response.Client;

import lombok.Getter;
import lombok.Setter;

/** Kết quả kiểm tra mã giảm giá ở trang giỏ hàng. */
@Getter
@Setter
public class CouponValidationResponse {

    private boolean valid;
    private String code;
    private Long discountAmount; // số tiền được giảm (0 nếu không hợp lệ)
    private String message; // thông báo tiếng Việt hiển thị inline dưới ô nhập mã

    public static CouponValidationResponse ok(String code, Long discountAmount) {
        CouponValidationResponse res = new CouponValidationResponse();
        res.setValid(true);
        res.setCode(code);
        res.setDiscountAmount(discountAmount);
        res.setMessage("Đã áp dụng mã giảm giá.");
        return res;
    }

    public static CouponValidationResponse invalid(String message) {
        CouponValidationResponse res = new CouponValidationResponse();
        res.setValid(false);
        res.setDiscountAmount(0L);
        res.setMessage(message);
        return res;
    }
}
