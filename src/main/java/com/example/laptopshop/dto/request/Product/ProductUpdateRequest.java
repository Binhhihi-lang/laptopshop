package com.example.laptopshop.dto.request.Product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Category;

import lombok.Getter;

@Getter
public class ProductUpdateRequest {

    @NotBlank(message = "PRODUCT_CODE_EMPTY")
    private String code;

    @NotBlank(message = "PRODUCT_NAME_EMPTY")
    private String name;

    @NotNull(message = "PRODUCT_PRICE_INVALID")
    private Long price;

    private String shortDesc;
    private String detailDesc;
    private Integer quantity;
    private Integer sold; // Cho phép admin sửa lại số lượt bán nếu cần đối soát thủ công
    private String factory;
    private String target;
    private String cpu;
    private String ram;
    private String storage;
    private String gpu;
    private String screen;
    private String os;
    private Double weight;
    private Integer warrantyMonths;
    private boolean active;
    private Category category;

    private MultipartFile inputFile; // Nhận ảnh mới nếu admin muốn đổi ảnh sản phẩm

}
