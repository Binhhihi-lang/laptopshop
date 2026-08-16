package com.example.laptopshop.dto.request.Product;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;

@Getter
public class ProductBulkStatusRequest {

    @NotEmpty(message = "INVALID_PRODUCT_DATA")
    private List<String> ids;

    private boolean active;

}
