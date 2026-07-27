package com.example.laptopshop.dto.response.Category;

import lombok.Getter;

@Getter
public class CategoryResponse {

    private String id;
    private String name;
    private String slug;
    private String description;
    private Integer displayOrder;
    private boolean active;
    private String image;

}
