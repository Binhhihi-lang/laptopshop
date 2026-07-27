package com.example.laptopshop.dto.response.Category;

import java.util.List;

import com.example.laptopshop.dto.response.Product.ProductResponse;
import lombok.Getter;

@Getter
public class CategoryDetailResponse {

    private String id;
    private String name;
    private String slug;
    private String description;
    private Integer displayOrder;
    private boolean active;
    private String image;
    private List<ProductResponse> products;

}
