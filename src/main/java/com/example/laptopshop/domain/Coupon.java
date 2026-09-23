package com.example.laptopshop.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "coupons")
@SQLDelete(sql = "UPDATE coupons SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL") // lấy all coupon chưa xóa mềm
@EntityListeners(AuditingEntityListener.class) // BẮT BUỘC để @CreatedDate/@LastModifiedDate được ghi
@Getter
@Setter
public class Coupon {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String code; // mã giảm giá, ví dụ "GIAM10"

    private Integer discountPercent; // Phần trăm giảm (0-100), để kiểu Integer để có thể nhận giá trị null

    private Long discountAmount; // Số tiền giảm trực tiếp (ví dụ: 50000), để kiểu Long cho đồng bộ với tiền tệ

    private LocalDateTime expiryDate; // ngày hết hạn sử dụng

    private Integer usageLimit = 100; // số lượt dùng tối đa

    private Integer usedCount = 0; // số lượt đã dùng

    private boolean active = true; // true: còn dùng được, false: đã khóa
    private String image; // Ảnh đại diện mã giảm giá (URL Cloudinary)

    // ===== v1: điều kiện áp dụng (tất cả nullable — null = không giới hạn, P3) =====
    // Coupon cũ trong DB có các cột này NULL → hành vi giữ nguyên như trước.

    private LocalDateTime startDate; // Bắt đầu được dùng; null = dùng ngay

    private Long minOrderValue; // Giá trị đơn tối thiểu; null = không yêu cầu

    private Long maxDiscountAmount; // Trần giảm tối đa (cho coupon %); null = không trần

    private Integer perUserLimit; // Số lần tối đa mỗi khách dùng; null = không giới hạn

    @Enumerated(EnumType.STRING)
    private ScopeType scopeType; // Phạm vi áp dụng; null = ALL (tương thích coupon cũ)

    @Column(name = "scope_value")
    private String scopeValue; // Định danh phạm vi: Category.id | Product.factory | Product.id.
                               // null khi scopeType = ALL. Khớp PromotionScope.targetValue (D18).

    @Enumerated(EnumType.STRING)
    private CouponType couponType; // PUBLIC | ASSIGNED | GIFT; null = PUBLIC (tương thích coupon cũ)

    @CreatedDate
    @Column(updatable = false) // Không bao giờ cho phép UPDATE cột này
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt; // Tự động ghi nhận mỗi khi UPDATE

    private LocalDateTime deletedAt; // null = chưa xóa, có giá trị = đã xóa mềm

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    // ===== Getter null-safe cho cột thêm ở Sprint 1 =====
    // Coupon cũ trong DB có các cột này = NULL. Getter *OrDefault trả giá trị mặc
    // định an toàn để logic kiểm tra điều kiện không phải tự check null (D22/R1).

    /** PUBLIC khi chưa gán — coupon cũ mặc định là mã công khai. */
    public CouponType getCouponTypeOrDefault() {
        return this.couponType == null ? CouponType.PUBLIC : this.couponType;
    }

    /** ORDER khi chưa gán — mặc định áp cho toàn bộ đơn hàng. */
    public ScopeType getScopeTypeOrDefault() {
        return this.scopeType == null ? ScopeType.ALL : this.scopeType;
    }

    /** 0 khi chưa gán — không yêu cầu giá trị đơn tối thiểu. */
    public long getMinOrderValueOrDefault() {
        return this.minOrderValue == null ? 0L : this.minOrderValue;
    }
}
