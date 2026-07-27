package com.example.laptopshop.dto.request.Category;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

import lombok.Getter;

@Getter
public class CategoryUpdateRequest {

    @NotBlank(message = "CATEGORY_NAME_REQUIRED")
    private String name;

    private String slug;
    private String description;
    private Integer displayOrder;
    private boolean active = true;

    private MultipartFile inputFile; // Nhận ảnh mới nếu admin muốn đổi ảnh danh mục

}
