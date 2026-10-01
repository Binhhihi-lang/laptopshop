package com.example.laptopshop.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Một voucher NẰM TRONG VÍ của khách (§3.4) — khác {@link Voucher} (mẫu voucher
 * do admin tạo). Claim một voucher sinh ra một bản ghi ở đây.
 *
 * <p>
 * Unique (user_id, voucher_id) chặn claim trùng (R17) — ràng buộc ở DB chứ không
 * chỉ ở service, vì 2 request claim song song vẫn lọt qua được bước kiểm tra.
 */
@Entity
@Table(name = "user_vouchers",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_user_vouchers_user_voucher",
                columnNames = { "user_id", "voucher_id" }))
@Getter
@Setter
public class UserVoucher {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserVoucherStatus status = UserVoucherStatus.AVAILABLE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserVoucherSource source = UserVoucherSource.CLAIMED;

    @Column(nullable = false)
    private LocalDateTime acquiredAt;

    /**
     * Hạn dùng, chép từ {@code voucher.expiryDate} lúc claim. null = trường tồn
     * (voucher không đặt hạn) — chép lại thay vì đọc voucher để sau này admin sửa
     * hạn voucher không làm đổi hạn của voucher đã phát.
     */
    private LocalDateTime expiresAt;

    private LocalDateTime usedAt;

    /** Đơn đã dùng voucher này; null khi chưa dùng. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    /** Còn dùng được tại {@code now}? */
    public boolean isAvailableAt(LocalDateTime now) {
        return this.status == UserVoucherStatus.AVAILABLE
                && (this.expiresAt == null || !now.isAfter(this.expiresAt));
    }
}
