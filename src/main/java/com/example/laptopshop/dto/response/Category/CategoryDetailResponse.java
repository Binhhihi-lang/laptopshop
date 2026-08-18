package com.example.laptopshop.dto.response.Category;

import java.time.LocalDateTime;
import java.util.List;

import com.example.laptopshop.dto.response.Product.ProductResponse;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

@Getter
@Setter
public class CategoryDetailResponse {

    private String id;
    private String name;
    private String description;
    private Integer displayOrder;
    private boolean active;
    private String image;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProductResponse> products;
}
