package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.service.CategoryService;
import com.example.laptopshop.service.UploadService;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryRestController {

    private final CategoryService categoryService;
    private final UploadService uploadService;

    public CategoryRestController(CategoryService categoryService, UploadService uploadService) {
        this.categoryService = categoryService;
        this.uploadService = uploadService;
    }

    // 1. Lấy danh sách danh mục
    @GetMapping
    public ResponseEntity<List<Category>> getAllCategories() {
        return ResponseEntity.ok(this.categoryService.getAllCategories());
    }

    // 2. Lấy chi tiết danh mục theo ID
    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable long id) {
        Category category = this.categoryService.getCategoryById(id);
        if (category == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(category);
    }

    // 3. Tạo mới danh mục (hỗ trợ upload ảnh danh mục)
    @PostMapping
    public ResponseEntity<Category> createCategory(
            @ModelAttribute Category newCategory,
            @RequestParam(value = "inputFile", required = false) MultipartFile file) {

        if (file != null && !file.isEmpty()) {
            String image = this.uploadService.handleSaveUploadFile(file, "category");
            newCategory.setImage(image);
        }

        Category savedCategory = this.categoryService.handleSaveCategory(newCategory);
        // Trả về 201 Created với dữ liệu danh mục vừa tạo
        return ResponseEntity.status(HttpStatus.CREATED).body(savedCategory);
    }

    // 4. Cập nhật danh mục
    @PutMapping("/{id}")
    public ResponseEntity<Category> updateCategory(
            @PathVariable long id,
            @ModelAttribute Category categoryUpdate,
            @RequestParam(value = "inputFile", required = false) MultipartFile file) {

        Category currentCategory = this.categoryService.getCategoryById(id);
        if (currentCategory == null) {
            return ResponseEntity.notFound().build();
        }

        currentCategory.setName(categoryUpdate.getName());
        currentCategory.setSlug(categoryUpdate.getSlug());
        currentCategory.setDescription(categoryUpdate.getDescription());
        currentCategory.setDisplayOrder(categoryUpdate.getDisplayOrder());
        currentCategory.setActive(categoryUpdate.isActive());

        if (file != null && !file.isEmpty()) {
            if (currentCategory.getImage() != null) {
                this.uploadService.handleDeleteFile(currentCategory.getImage(), "category");
            }
            String newImage = this.uploadService.handleSaveUploadFile(file, "category");
            currentCategory.setImage(newImage);
        }

        Category updatedCategory = this.categoryService.handleSaveCategory(currentCategory);
        // Trả về 200 OK với dữ liệu danh mục đã cập nhật
        return ResponseEntity.ok(updatedCategory);
    }

    // 5. Xóa danh mục
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable long id) {
        Category category = this.categoryService.getCategoryById(id);

        if (category == null) {
            return ResponseEntity.notFound().build();
        }

        if (category.getImage() != null) {
            this.uploadService.handleDeleteFile(category.getImage(), "category");
        }

        this.categoryService.deleteCategoryById(id);
        // Trả về 204 No Content để xác nhận xóa thành công
        return ResponseEntity.noContent().build();
    }
}