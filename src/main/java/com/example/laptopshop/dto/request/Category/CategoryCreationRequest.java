package com.example.laptopshop.dto.request.Category;

import jakarta.validation.constraints.NotBlank;
import org.springframework.web.multipart.MultipartFile;

public class CategoryCreationRequest {

    @NotBlank(message = "CATEGORY_NAME_REQUIRED")
    private String name;

    private String slug;
    private String description;
    private Integer displayOrder;

    private MultipartFile inputFile; // Hứng file ảnh danh mục trực tiếp trong DTO này luôn, giống UserCreationRequest

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public MultipartFile getInputFile() {
        return inputFile;
    }

    public void setInputFile(MultipartFile inputFile) {
        this.inputFile = inputFile;
    }
}
