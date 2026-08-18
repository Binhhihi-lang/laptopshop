package com.example.laptopshop.domain;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@SQLDelete(sql = "UPDATE categories SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL") // lấy all category chưa xóa mềm
@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name; // Tên danh mục, ví dụ: "Laptop Gaming", "Laptop Văn phòng", "Laptop Đồ họa"
    private String description; // Mô tả ngắn nhóm nhu cầu, ví dụ: laptop cấu hình cao cho game
    private String image; // Ảnh đại diện danh mục (tên file, lưu trong thư mục uploads)
    private int displayOrder; // Thứ tự hiển thị ngoài trang chủ
    private boolean active = true; // true: đang hiển thị, false: ẩn
    @CreatedDate
    @Column(updatable = false) // Không bao giờ cho phép UPDATE cột này
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt; // Tự động ghi nhận mỗi khi UPDATE

    private LocalDateTime deletedAt; // null = chưa xóa, có giá trị = đã xóa mềm

    // 1 category - nhiều product
    @OneToMany(mappedBy = "category")
    private List<Product> products;

    @Override
    public String toString() {
        return "Category [id=" + id + ", name=" + name + ", description=" + description
                + ", image=" + image + ", displayOrder=" + displayOrder + ", active=" + active + "]";
    }
}
