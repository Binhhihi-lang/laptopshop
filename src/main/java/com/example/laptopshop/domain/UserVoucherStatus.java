package com.example.laptopshop.domain;

/** Trạng thái một voucher trong ví khách (§3.4). */
public enum UserVoucherStatus {
    /** Còn dùng được. */
    AVAILABLE,
    /** Đã áp vào một đơn. */
    USED,
    /** Quá hạn mà chưa dùng — job dọn chuyển sang trạng thái này. */
    EXPIRED
}
