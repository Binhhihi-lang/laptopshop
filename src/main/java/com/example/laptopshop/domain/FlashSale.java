package com.example.laptopshop.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** Phiên flash sale: đổi giá ngay trên card, có khung giờ ngắn và kho riêng (§0.5). */
@Entity
@Table(name = "flash_sales")
@SQLDelete(sql = "UPDATE flash_sales SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class FlashSale {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String name;

    private String description;

    /** Ảnh banner đầu trang /flash-sale (URL Cloudinary). */
    private String bannerImage;

    @Column(nullable = false)
    private LocalDateTime startAt;

    @Column(nullable = false)
    private LocalDateTime endAt;

    /** Công tắc khẩn cấp; vẫn phải trong khung giờ mới có tác dụng. */
    private boolean active = true;

    @OneToMany(mappedBy = "flashSale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<FlashSaleItem> items = new ArrayList<>();

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    /** Phiên đang diễn ra tại {@code now}? */
    public boolean isRunning(LocalDateTime now) {
        return this.active
                && this.startAt != null && this.endAt != null
                && !now.isBefore(this.startAt) && !now.isAfter(this.endAt);
    }
}
