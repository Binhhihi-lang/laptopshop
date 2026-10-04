package com.example.laptopshop.dto.request.Client;

import lombok.Getter;
import lombok.Setter;

/**
 * Body cho POST /api/v1/client/vouchers/validate.
 *
 * FE cần hỏi BE số tiền được giảm NGAY Ở TRANG GIỎ (trước khi đặt hàng) để
 * hiển thị đúng dòng "Giảm giá" trong tóm tắt đơn.
 *
 * <p>
 * D14: KHÔNG có {@code orderTotal} — BE tự đọc giỏ của khách và tự chạy engine.
 * FE gửi số tiền lên thì sửa được giá (D6) và con số preview lệch với lúc chốt
 * đơn vì thiếu kết quả promotion trên từng dòng.
 *
 * <p>
 * Voucher chỉ vào đơn qua VÍ (đã bỏ đường gõ mã tay) nên chỉ nhận
 * {@code userVoucherId}.
 */
@Getter
@Setter
public class ValidateVoucherRequest {

    /** Voucher trong ví khách. */
    private String userVoucherId;
}
