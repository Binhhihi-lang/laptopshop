package com.example.laptopshop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.dto.request.Category.CategoryCreationRequest;
import com.example.laptopshop.dto.request.Category.CategoryUpdateRequest;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final UploadService uploadService;

    public CategoryService(CategoryRepository categoryRepository, UploadService uploadService) {
        this.categoryRepository = categoryRepository;
        this.uploadService = uploadService;
    }

    public List<Category> getAllCategories() {
        return this.categoryRepository.findAll();
    }

    public Category getCategoryById(long id) {
        Category category = this.categoryRepository.findById(id);
        if (category == null) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    public void deleteCategoryById(long id) {
        Category category = getCategoryById(id); // kiểm tra tồn tại, nếu không thì throw lỗi
        this.categoryRepository.delete(category);
    }

    // Nhận DTO từ Controller, validate dữ liệu thô, map sang Entity, xử lý ảnh
    // rồi lưu DB. Controller không còn hứng trực tiếp bằng Entity Category nữa,
    // giống cách làm với User/Product.
    public Category handleCreateCategory(CategoryCreationRequest request) {
        // 1. Validate dữ liệu thô từ DTO
        String normalizedName = request.getName().trim();
        validateName(normalizedName, null);

        // 2. Map dữ liệu từ DTO sang Entity Category
        Category newCategory = new Category();
        newCategory.setName(normalizedName);
        newCategory.setSlug(request.getSlug());
        newCategory.setDescription(request.getDescription());
        newCategory.setDisplayOrder(request.getDisplayOrder());

        // 3. Xử lý upload ảnh danh mục nếu có
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String image = this.uploadService.handleSaveUploadFile(file, "category");
            newCategory.setImage(image);
        }

        return this.categoryRepository.save(newCategory);
    }

    public Category handleUpdateCategory(long id, CategoryUpdateRequest request) {
        // 1. Tìm Category cũ trong DB, không thấy thì ném lỗi
        Category existingCategory = getCategoryById(id);

        validateName(request.getName(), id);

        // 3. Đổ dữ liệu mới từ DTO đè lên Entity cũ
        existingCategory.setName(request.getName().trim());
        existingCategory.setSlug(request.getSlug());
        existingCategory.setDescription(request.getDescription());
        existingCategory.setDisplayOrder(request.getDisplayOrder());
        existingCategory.setActive(request.isActive());

        // 4. Xử lý đổi ảnh mới nếu admin gửi lên file mới, xóa ảnh cũ trước khi lưu ảnh
        // mới
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            if (existingCategory.getImage() != null) {
                this.uploadService.handleDeleteFile(existingCategory.getImage());
            }
            String newImage = this.uploadService.handleSaveUploadFile(file, "category");
            existingCategory.setImage(newImage);
        }

        return this.categoryRepository.save(existingCategory);
    }

    // Validate tên danh mục, nếu trùng với danh mục khác (khác id) thì ném lỗi 2
    // trường hợp: create (currentId = null) và update (currentId = id)
    // kiểu Long
    private void validateName(String name, Long currentId) {
        if (name == null || name.isBlank()) {
            throw new AppException(ErrorCode.CATEGORY_NAME_REQUIRED);
        }
        String normalizedName = name.trim();
        boolean exists = currentId == null
                ? this.categoryRepository.existsByNameIgnoreCase(normalizedName)
                : this.categoryRepository.existsByNameIgnoreCaseAndIdNot(normalizedName, currentId);

        if (exists) {
            throw new AppException(ErrorCode.CATEGORY_ALREADY_EXISTS);
        }
    }

}