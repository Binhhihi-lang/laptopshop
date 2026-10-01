package com.example.laptopshop.domain;

/** Voucher vào ví khách bằng đường nào (§3.4). */
public enum UserVoucherSource {
    /** Khách tự bấm "Lưu mã". */
    CLAIMED,
    /** Admin tặng. */
    GIFTED
}
