package com.example.laptopshop.dto.response.UserVoucher;

import java.time.LocalDateTime;

import com.example.laptopshop.domain.UserVoucherSource;
import com.example.laptopshop.domain.UserVoucherStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một voucher đã phát, nhìn từ phía quản trị — trả kèm thông tin KHÁCH đã nhận.
 *
 * <p>
 * Khác {@link UserVoucherResponse} (dùng cho ví của chính khách, không cần biết
 * khách là ai): ở đây admin cần thấy ai đang giữ voucher để đối chiếu.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoucherHolderResponse {

    private String id;

    private String userId;
    private String userName;
    private String userEmail;

    private UserVoucherSource source;
    private UserVoucherStatus status;

    private LocalDateTime acquiredAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
