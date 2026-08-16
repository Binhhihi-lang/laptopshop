package com.example.laptopshop.domain;

import java.time.LocalDateTime;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@SQLDelete(sql = "UPDATE products SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL") // lấy all product
@Entity
@Table(name = "products")
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // Mã SKU sản phẩm, ví dụ "IP15PM-256" — CHỈ dùng để hiển thị/tìm kiếm,
    // KHÔNG dùng để join dữ liệu (OrderDetail vẫn join qua id chuẩn quan hệ).
    @Column(unique = true)
    private String code;

    private String name;
    private long price;
    private String image;
    private String detailDesc;
    private String shortDesc;
    private long quantity;
    private long sold;
    private String factory;
    private String target;

    // Cấu hình laptop dùng để hiển thị chi tiết và lọc sản phẩm.
    private String cpu;
    private String ram;
    private String storage;
    private String gpu;
    private String screen;
    private String os;
    private double weight;
    private int warrantyMonths;

    private boolean active = true; // true: đang bán, false: ẩn/ngừng bán

    @CreatedDate
    @Column(updatable = false) // Không bao giờ cho phép UPDATE cột này
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt; // Tự động ghi nhận mỗi khi UPDATE

    private LocalDateTime deletedAt; // null = chưa xóa, có giá trị = đã xóa mềm

    // many product - 1 category
    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @Override
    public String toString() {
        return "Product [id=" + id + ", code=" + code + ", name=" + name + ", price=" + price + ", image=" + image
                + ", detailDesc=" + detailDesc + ", shortDesc=" + shortDesc + ", quantity=" + quantity + ", sold="
                + sold + ", factory=" + factory + ", target=" + target + ", cpu=" + cpu + ", ram=" + ram
                + ", storage=" + storage + ", gpu=" + gpu + ", screen=" + screen + ", os=" + os + ", weight="
                + weight + ", warrantyMonths=" + warrantyMonths + ", active=" + active + "]";
    }
}
