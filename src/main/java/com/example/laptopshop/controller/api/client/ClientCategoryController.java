package com.example.laptopshop.controller.api.client;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Category.CategoryResponse;
import com.example.laptopshop.service.CategoryService;

/**
 * Storefront: danh sách category công khai.
 * Đường dẫn: /api/v1/client/categories
 * Bảo mật: permitAll() ở tầng SecurityConfiguration; controller này không cần @PreAuthorize.
 * Quy tắc storefront: chỉ trả category đang active, sắp xếp theo displayOrder.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/client/categories")
public class ClientCategoryController {

    CategoryService categoryService;

    @GetMapping
    public ApiResponse<List<CategoryResponse>> getActiveCategories() {
        ApiResponse<List<CategoryResponse>> response = new ApiResponse<>();
        response.setResult(this.categoryService.getActiveCategoryResponses());
        return response;
    }
}
