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

/** Slide carousel trang chủ — khối hiển thị, không đụng logic tiền (§0.6). */
@Entity
@Table(name = "home_banners")
@SQLDelete(sql = "UPDATE home_banners SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class HomeBanner {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String title;

    private String subtitle;

    /** Ảnh slide (URL Cloudinary). */
    @Column(nullable = false)
    private String image;

    /** Màu nền chữ khi ảnh chưa tải kịp; null = FE dùng nền mặc định. */
    private String bgColor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BannerTargetType targetType;

    /** Định danh theo {@code targetType}; validate ở HomeBannerService (D30). */
    @Column(nullable = false)
    private String targetValue;

    /** Nhỏ hiển thị trước. */
    @Column(nullable = false)
    private Integer sortOrder = 0;

    private boolean active = true;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        if (this.sortOrder == null) {
            this.sortOrder = 0;
        }
    }
}
