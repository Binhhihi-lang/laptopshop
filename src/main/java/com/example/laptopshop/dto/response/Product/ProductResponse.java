package com.example.laptopshop.dto.response.Product;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ProductResponse {

    private String id;
    private String code;
    private String name;
    private Long price;
    private String image;
    private String shortDesc;
    private String detailDesc;
    private Integer quantity;
    private Integer sold;
    private String factory;
    private String target;
    private boolean active;
    private String cpu;
    private String ram;
    private String storage;
    private String gpu;
    private String screen;
    private String os;
    private Double weight;
    private Integer warrantyMonths;
    private String categoryId;
    private String categoryName;

}
