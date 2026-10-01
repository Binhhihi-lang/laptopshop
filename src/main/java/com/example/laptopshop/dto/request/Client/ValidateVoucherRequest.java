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
 * BR-V13: nhận ĐÚNG MỘT trong hai — {@code code} (mã gõ tay) hoặc
 * {@code userVoucherId} (voucher trong ví). Trước đây chỉ có {@code code}, nên
 * nhánh chọn voucher từ ví không có đường nào hỏi BE → FE phải tự tính, và số
 * hiển thị lệch với số BE thu (bỏ qua phạm vi voucher).
 */
@Getter
@Setter
public class ValidateVoucherRequest {

    /** Mã gõ tay. Bỏ trống khi dùng {@link #userVoucherId}. */
    private String code;

    /** Voucher trong ví khách. Bỏ trống khi dùng {@link #code}. */
    private String userVoucherId;
}
