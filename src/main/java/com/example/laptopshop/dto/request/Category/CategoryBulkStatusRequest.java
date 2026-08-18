package com.example.laptopshop.dto.request.Category;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;

@Getter
public class CategoryBulkStatusRequest {

    @NotEmpty(message = "INVALID_CATEGORY_DATA")
    private List<String> ids;

    private boolean active;

}
