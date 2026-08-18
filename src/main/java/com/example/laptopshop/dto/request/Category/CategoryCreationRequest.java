package com.example.laptopshop.dto.request.Category;

import jakarta.validation.constraints.NotBlank;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import lombok.Getter;

@Getter
@Setter
public class CategoryCreationRequest {

    @NotBlank(message = "CATEGORY_NAME_REQUIRED")
    private String name;

    private String description;
    private Integer displayOrder;
    private boolean active = true ;
    private MultipartFile inputFile; // Hứng file ảnh danh mục trực tiếp trong DTO
}
