package com.example.laptopshop.dto.response.Product;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ProductResponse {

    private String id;
    private String code;
    private String name;
    private Long price;
    private Long originalPrice;
    private String image;
    private String shortDesc;
    private String detailDesc;
    private Integer quantity;
    private Integer sold;
    private String factory;
    private String target;
    private boolean active;
    private String cpu;
    private String ram;
    private String storage;
    private String gpu;
    private String screen;
    private String os;
    private Double weight;
    private Integer warrantyMonths;
    private String categoryId;
    private String categoryName;
    private Boolean categoryActive; // trạng thái active của Category (null nếu category bị xóa mềm)

    // ===== Flash sale (Sprint 2b) — null nếu sản phẩm không trong phiên nào =====
    private Long flashPrice;      // giá sốc thay price khi phiên đang chạy (§0.5)
    private Integer flashStock;   // kho riêng của phiên
    private Integer flashSold;    // đã bán trong phiên (progress "Đã bán x/y")
    private String flashSaleId;   // phiên chứa sản phẩm
    private LocalDateTime flashEndAt; // lúc phiên kết thúc (đếm ngược)

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
