package com.example.laptopshop.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Voucher;
import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.domain.UserVoucher;
import com.example.laptopshop.domain.UserVoucherSource;
import com.example.laptopshop.domain.UserVoucherStatus;
import com.example.laptopshop.domain.VoucherType;
import com.example.laptopshop.dto.response.UserVoucher.VoucherHolderResponse;
import com.example.laptopshop.dto.response.UserVoucher.UserVoucherResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.VoucherRepository;
import com.example.laptopshop.repository.UserRepository;
import com.example.laptopshop.repository.UserVoucherRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Ví voucher của khách (§3.4): claim, xem, đánh dấu đã dùng, hoàn khi hủy đơn.
 *
 * <p>
 * Tách khỏi {@link VoucherService} vì đây là vòng đời voucher CỦA MỘT KHÁCH, còn
 * VoucherService là CRUD mẫu voucher của admin.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VoucherWalletService {

    UserVoucherRepository userVoucherRepository;
    VoucherRepository voucherRepository;
    UserRepository userRepository;
    VoucherService voucherService;

    /**
     * Khách bấm "Lưu mã". Sinh một {@link UserVoucher} từ voucher PUBLIC.
     *
     * <p>
     * Chặn claim trùng ở đây để báo lỗi thân thiện, nhưng unique index
     * {@code (user_id, voucher_id)} ở DB mới là ràng buộc thật — 2 request song
     * song vẫn lọt qua được bước kiểm tra này.
     */
    @Transactional
    public UserVoucherResponse claim(String userId, String voucherId) {
        Voucher template = this.voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_USABLE));
        if (template.getVoucherType() != null
                && template.getVoucherType() != com.example.laptopshop.domain.VoucherType.PUBLIC) {
            throw new AppException(ErrorCode.USER_VOUCHER_NOT_CLAIMABLE);
        }
        if (!this.voucherService.isVoucherUsable(template)) {
            throw new AppException(ErrorCode.VOUCHER_NOT_USABLE);
        }
        if (this.userVoucherRepository.existsByUserIdAndVoucherId(userId, template.getId())) {
            throw new AppException(ErrorCode.USER_VOUCHER_ALREADY_CLAIMED);
        }
        // Hết lượt phát (usageLimit) tính trên tổng voucher đã phát ra, không phải
        // trên số đơn đã dùng — voucher nằm trong ví chưa dùng vẫn chiếm suất.
        long issued = this.userVoucherRepository.countByVoucherId(template.getId());
        if (template.getUsageLimit() != null && template.getUsageLimit() > 0
                && issued >= template.getUsageLimit()) {
            throw new AppException(ErrorCode.USER_VOUCHER_OUT_OF_STOCK);
        }

        User user = this.userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserVoucher userVoucher = new UserVoucher();
        userVoucher.setUser(user);
        userVoucher.setVoucher(template);
        userVoucher.setStatus(UserVoucherStatus.AVAILABLE);
        userVoucher.setSource(UserVoucherSource.CLAIMED);
        userVoucher.setAcquiredAt(LocalDateTime.now());
        // Chép hạn từ mẫu: admin sửa hạn sau không làm đổi hạn voucher đã phát.
        userVoucher.setExpiresAt(template.getExpiryDate());

        return toResponse(this.userVoucherRepository.save(userVoucher));
    }

    /**
     * Danh sách khách đã nhận một voucher — bảng "Ví voucher" ở trang chi tiết.
     */
    @Transactional(readOnly = true)
    public List<VoucherHolderResponse> getHolders(String voucherId) {
        return this.userVoucherRepository.findHoldersByVoucherId(voucherId).stream()
                .map(voucher -> {
                    User user = voucher.getUser();
                    return VoucherHolderResponse.builder()
                            .id(voucher.getId())
                            .userId(user == null ? null : user.getId())
                            .userName(user == null ? null : user.getFullName())
                            .userEmail(user == null ? null : user.getEmail())
                            .source(voucher.getSource())
                            .status(voucher.getStatus())
                            .acquiredAt(voucher.getAcquiredAt())
                            .expiresAt(voucher.getExpiresAt())
                            .usedAt(voucher.getUsedAt())
                            .build();
                })
                .toList();
    }

    /** Ví của khách; {@code status} null = cả ví. */
    @Transactional(readOnly = true)
    public List<UserVoucherResponse> getMyVouchers(String userId, UserVoucherStatus status) {
        List<UserVoucher> vouchers = status == null
                ? this.userVoucherRepository.findByUserIdOrderByAcquiredAtDesc(userId)
                : this.userVoucherRepository.findByUserIdAndStatusOrderByAcquiredAtDesc(userId, status);
        return vouchers.stream().map(this::toResponse).toList();
    }

    /**
     * Admin phát voucher cho một nhóm khách (voucher ASSIGNED).
     *
     * <p>
     * Bỏ qua khách đã có voucher này thay vì ném lỗi — admin gán một danh sách
     * dài thì vài người trùng là chuyện thường, chặn cả lô vì một người là sai.
     * Trả về số voucher thực sự phát thêm.
     *
     * <p>
     * Khác {@link #claim}: đây là admin phát đích danh, không hỏi
     * {@code voucherType} — voucher PUBLIC cũng gán tay được.
     */
    @Transactional
    public int assignToUsers(String voucherId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return 0;
        }
        Voucher template = this.voucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException(ErrorCode.VOUCHER_NOT_USABLE));
        if (!this.voucherService.isVoucherUsable(template)) {
            throw new AppException(ErrorCode.VOUCHER_NOT_USABLE);
        }

        int issued = 0;
        for (String userId : userIds) {
            if (this.userVoucherRepository.existsByUserIdAndVoucherId(userId, voucherId)) {
                continue; // đã có rồi, không phát trùng
            }
            User user = this.userRepository.findById(userId).orElse(null);
            if (user == null) {
                continue; // id lạ thì bỏ qua, không chặn cả lô
            }
            UserVoucher userVoucher = new UserVoucher();
            userVoucher.setUser(user);
            userVoucher.setVoucher(template);
            userVoucher.setStatus(UserVoucherStatus.AVAILABLE);
            // Voucher gán đích danh = "được phát"; chỉ voucher PUBLIC khách tự
            // nhận mới là "tự nhận" (CLAIMED). Đừng gán cứng GIFTED cho mọi lô.
            userVoucher.setSource(template.getVoucherType() == VoucherType.PUBLIC
                    ? UserVoucherSource.CLAIMED
                    : UserVoucherSource.GIFTED);
            userVoucher.setAcquiredAt(LocalDateTime.now());
            userVoucher.setExpiresAt(template.getExpiryDate());
            this.userVoucherRepository.save(userVoucher);
            issued++;
        }
        return issued;
    }

    /**
     * Tra voucher để áp vào đơn, kiểm tra thuộc đúng khách và còn dùng được.
     * Ném lỗi thay vì trả null vì lúc này khách đã bấm "Đặt hàng" — không được
     * âm thầm bỏ voucher và thu nhiều tiền hơn khách tưởng.
     */
    @Transactional(readOnly = true)
    public UserVoucher getUsableVoucher(String userId, String userVoucherId) {
        UserVoucher voucher = this.userVoucherRepository.findByIdAndUserId(userVoucherId, userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_VOUCHER_NOT_FOUND));
        if (voucher.getStatus() == UserVoucherStatus.USED) {
            throw new AppException(ErrorCode.USER_VOUCHER_ALREADY_USED);
        }
        if (!voucher.isAvailableAt(LocalDateTime.now())) {
            throw new AppException(ErrorCode.USER_VOUCHER_EXPIRED);
        }
        return voucher;
    }

    /** Đánh dấu voucher đã dùng cho một đơn. Chỗ gọi phải cùng transaction tạo đơn. */
    @Transactional
    public void markUsed(UserVoucher voucher, Order order) {
        voucher.setStatus(UserVoucherStatus.USED);
        voucher.setUsedAt(LocalDateTime.now());
        voucher.setOrder(order);
        this.userVoucherRepository.save(voucher);
    }

    /**
     * Hoàn voucher khi hủy đơn (D12). Chỉ hoàn nếu còn hạn; quá hạn rồi thì
     * chuyển EXPIRED để ví không hiện một voucher dùng được nhưng thực tế không.
     */
    @Transactional
    public void releaseOnCancel(UserVoucher voucher) {
        if (voucher == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        boolean stillValid = voucher.getExpiresAt() == null || !now.isAfter(voucher.getExpiresAt());
        voucher.setStatus(stillValid ? UserVoucherStatus.AVAILABLE : UserVoucherStatus.EXPIRED);
        voucher.setUsedAt(null);
        voucher.setOrder(null);
        this.userVoucherRepository.save(voucher);
    }

    /** Job dọn: voucher quá hạn mà còn AVAILABLE → EXPIRED. Trả về số bản ghi đổi. */
    @Transactional
    public int expireOverdue() {
        List<UserVoucher> overdue = this.userVoucherRepository
                .findByStatusAndExpiresAtBefore(UserVoucherStatus.AVAILABLE, LocalDateTime.now());
        overdue.forEach(v -> v.setStatus(UserVoucherStatus.EXPIRED));
        this.userVoucherRepository.saveAll(overdue);
        return overdue.size();
    }

    /** Tra voucher đang gắn với một đơn — dùng khi hủy đơn để hoàn (D12). */
    @Transactional(readOnly = true)
    public UserVoucher findByOrderId(String orderId) {
        return this.userVoucherRepository.findByOrderId(orderId).orElse(null);
    }

    private UserVoucherResponse toResponse(UserVoucher userVoucher) {
        Voucher template = userVoucher.getVoucher();
        return UserVoucherResponse.builder()
                .id(userVoucher.getId())
                .voucherId(template == null ? null : template.getId())
                .code(template == null ? null : template.getCode())
                .image(template == null ? null : template.getImage())
                .discountPercent(template == null ? null : template.getDiscountPercent())
                .discountAmount(template == null ? null : template.getDiscountAmount())
                .minOrderValue(template == null ? null : template.getMinOrderValue())
                .maxDiscountAmount(template == null ? null : template.getMaxDiscountAmount())
                .status(userVoucher.getStatus())
                .source(userVoucher.getSource())
                .acquiredAt(userVoucher.getAcquiredAt())
                .expiresAt(userVoucher.getExpiresAt())
                .usedAt(userVoucher.getUsedAt())
                .build();
    }
}
