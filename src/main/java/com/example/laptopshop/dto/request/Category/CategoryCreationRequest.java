package com.example.laptopshop.dto.request.Category;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;
import lombok.Getter;

@Getter

public class CategoryCreationRequest {

    @NotBlank(message = "CATEGORY_NAME_REQUIRED")
    private String name;

    private String slug;
    private String description;
    private Integer displayOrder;
    private boolean active = true ;
    private MultipartFile inputFile; // Hứng file ảnh danh mục trực tiếp trong DTO
}
