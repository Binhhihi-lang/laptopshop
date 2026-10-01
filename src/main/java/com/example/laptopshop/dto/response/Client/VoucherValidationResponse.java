package com.example.laptopshop.dto.response.Client;

import lombok.Getter;
import lombok.Setter;

/** Kết quả kiểm tra voucher ở trang giỏ hàng. */
@Getter
@Setter
public class VoucherValidationResponse {

    private boolean valid;
    private String code;
    private Long discountAmount; // số tiền được giảm (0 nếu không hợp lệ)
    private String message; // thông báo tiếng Việt hiển thị inline dưới ô nhập mã
    /**
     * Phần mệnh giá voucher KHÔNG dùng được vì đơn nhỏ hơn mệnh giá (BR-V14).
     * {@code > 0} = khách mất phần này, không được hoàn lại; FE hiện cảnh báo.
     * Không tính phần bị cắt bởi trần {@code maxDiscountAmount} — đó là thiết kế.
     */
    private Long forfeitedAmount;

    public static VoucherValidationResponse ok(String code, Long discountAmount, Long forfeitedAmount) {
        VoucherValidationResponse res = new VoucherValidationResponse();
        res.setValid(true);
        res.setCode(code);
        res.setDiscountAmount(discountAmount);
        res.setForfeitedAmount(forfeitedAmount);
        res.setMessage("Đã áp dụng voucher.");
        return res;
    }

    public static VoucherValidationResponse invalid(String message) {
        VoucherValidationResponse res = new VoucherValidationResponse();
        res.setValid(false);
        res.setDiscountAmount(0L);
        res.setForfeitedAmount(0L);
        res.setMessage(message);
        return res;
    }
}
