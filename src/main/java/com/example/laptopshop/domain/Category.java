package com.example.laptopshop.domain;

import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String name; // Tên danh mục, ví dụ: "Laptop Gaming", "Laptop Văn phòng", "Laptop Đồ họa"
    private String slug; // Dùng cho URL/lọc, ví dụ: laptop-gaming, laptop-van-phong
    private String description; // Mô tả ngắn nhóm nhu cầu, ví dụ: laptop cấu hình cao cho game
    private String image; // Ảnh đại diện danh mục (tên file, lưu trong thư mục uploads)
    private int displayOrder; // Thứ tự hiển thị ngoài trang chủ
    private boolean active = true; // true: đang hiển thị, false: ẩn

    // 1 category - nhiều product
    @OneToMany(mappedBy = "category")
    private List<Product> products;

    @Override
    public String toString() {
        return "Category [id=" + id + ", name=" + name + ", slug=" + slug + ", description=" + description
                + ", image=" + image + ", displayOrder=" + displayOrder + ", active=" + active + "]";
    }
}
