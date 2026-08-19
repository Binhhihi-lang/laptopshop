package com.example.laptopshop.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
}
