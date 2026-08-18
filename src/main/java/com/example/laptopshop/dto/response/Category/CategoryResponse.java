package com.example.laptopshop.dto.response.Category;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class CategoryResponse {

    private String id;
    private String name;
    private String description;
    private Integer displayOrder;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String image;
    private Long productCount; // số lượng sản phẩm thuộc danh mục (trang danh sách)

}
