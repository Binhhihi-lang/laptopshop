package com.example.laptopshop.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Dọn voucher quá hạn trong ví khách.
 *
 * <p>
 * {@code VoucherWalletService.expireOverdue()} đã có sẵn từ Sprint 3 nhưng chưa
 * được gọi định kỳ, nên voucher hết hạn vẫn nằm ở trạng thái {@code AVAILABLE}
 * và hiện trong tab "Khả dụng" của ví — khách chỉ biết voucher không dùng được
 * khi bấm áp dụng và bị chặn.
 *
 * <p>
 * Luồng chốt đơn KHÔNG phụ thuộc job này: {@code getUsableVoucher} kiểm tra hạn
 * thật chứ không tin {@code status}. Job chỉ để ví hiển thị đúng sự thật.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class VoucherExpiryJob {

    /** Chạy mỗi giờ — cùng nhịp với PaymentCleanupJob, đủ nhanh cho hạn theo ngày. */
    static final String CRON_HOURLY = "0 0 * * * *";

    VoucherWalletService voucherWalletService;

    @Scheduled(cron = CRON_HOURLY)
    public void expireOverdueVouchers() {
        int changed = this.voucherWalletService.expireOverdue();
        if (changed > 0) {
            log.info("Đã chuyển {} voucher quá hạn sang EXPIRED", changed);
        }
    }
}
