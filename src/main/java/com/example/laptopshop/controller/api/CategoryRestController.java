package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.dto.request.Category.CategoryCreationRequest;
import com.example.laptopshop.dto.request.Category.CategoryUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.service.CategoryService;
import com.example.laptopshop.service.UploadService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/admin/categories")
public class CategoryRestController {

    private final CategoryService categoryService;
    private final UploadService uploadService;

    public CategoryRestController(CategoryService categoryService, UploadService uploadService) {
        this.categoryService = categoryService;
        this.uploadService = uploadService;
    }

    // 1. Lấy danh sách danh mục
    @GetMapping
    public ApiResponse<List<Category>> getAllCategories() {
        ApiResponse<List<Category>> response = new ApiResponse<>();
        response.setResult(this.categoryService.getAllCategories());
        return response;
    }

    // 2. Lấy chi tiết danh mục theo ID
    @GetMapping("/{id}")
    public ApiResponse<Category> getCategoryById(@PathVariable long id) {
        Category category = this.categoryService.getCategoryById(id);
        ApiResponse<Category> response = new ApiResponse<>();
        response.setResult(category);
        return response;
    }

    // 3. Tạo mới danh mục (nhận dữ liệu dạng form-data để hỗ trợ upload ảnh danh
    // mục, giống cách làm với User/Product -> Controller không còn hứng trực
    // tiếp bằng Entity Category nữa, toàn bộ map dữ liệu/validate/xử lý ảnh nằm
    // ở Service)
    @PostMapping
    public ApiResponse<Category> createCategory(@Valid @ModelAttribute CategoryCreationRequest request) {
        Category savedCategory = this.categoryService.handleCreateCategory(request);
        ApiResponse<Category> response = new ApiResponse<>();
        response.setResult(savedCategory);
        return response;
    }

    // Cập nhật danh mục
    @PutMapping("/{id}")
    public ApiResponse<Category> updateCategory(@PathVariable long id,
            @Valid @ModelAttribute CategoryUpdateRequest request) {
        Category updatedCategory = this.categoryService.handleUpdateCategory(id, request);
        ApiResponse<Category> response = new ApiResponse<>();
        response.setResult(updatedCategory);
        return response;
    }

    // 5. Xóa danh mục (xóa ảnh trước, giống cách UserRestController xóa avatar
    // trước khi xóa User)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable long id) {
        Category category = this.categoryService.getCategoryById(id);

        if (category.getImage() != null) {
            this.uploadService.handleDeleteFile(category.getImage());
        }

        this.categoryService.deleteCategoryById(id);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setResult(null);
        return response;
    }
}