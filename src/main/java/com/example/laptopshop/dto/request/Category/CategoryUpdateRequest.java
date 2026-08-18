package com.example.laptopshop.dto.request.Category;

import jakarta.validation.constraints.NotBlank;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

import lombok.Getter;

@Getter
@Setter
public class CategoryUpdateRequest {

    @NotBlank(message = "CATEGORY_NAME_REQUIRED")
    private String name;

    private String description;
    private Integer displayOrder;
    private boolean active = true;

    private MultipartFile inputFile; // Nhận ảnh mới nếu admin muốn đổi ảnh danh mục

    private boolean removeImage = false; // true = xóa ảnh hiện tại nếu không gửi file mới


}
