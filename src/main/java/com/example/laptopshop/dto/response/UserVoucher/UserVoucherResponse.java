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
 * Một voucher trong ví khách (§3.4). Trả kèm thông tin hiển thị của voucher
 * (mã, mức giảm, ảnh) để FE không phải gọi thêm API tra voucher.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVoucherResponse {

    private String id;

    /** Voucher.id — cần khi khách bấm "Dùng ngay" để gửi lên lúc đặt hàng. */
    private String voucherId;

    private String code;
    private String image;
    private Integer discountPercent;
    private Long discountAmount;
    private Long minOrderValue;
    private Long maxDiscountAmount;

    private UserVoucherStatus status;
    private UserVoucherSource source;

    private LocalDateTime acquiredAt;
    /** null = voucher không đặt hạn (trường tồn). */
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
