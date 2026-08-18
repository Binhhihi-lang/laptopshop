package com.example.laptopshop.controller.api;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.dto.request.Category.CategoryCreationRequest;
import com.example.laptopshop.dto.request.Category.CategoryUpdateRequest;
import com.example.laptopshop.dto.request.Category.CategoryBulkDeleteRequest;
import com.example.laptopshop.dto.request.Category.CategoryBulkStatusRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Category.CategoryDetailResponse;
import com.example.laptopshop.dto.response.Category.CategoryResponse;
import com.example.laptopshop.service.CategoryService;
import com.example.laptopshop.service.UploadService;

import jakarta.validation.Valid;
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/admin/categories")
public class CategoryRestController {

    CategoryService categoryService;

    // 1. Lấy danh sách danh mục (KHÔNG kèm danh sách sản phẩm, dùng cho
    // sidebar/dropdown/bảng danh sách)
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        ApiResponse<List<CategoryResponse>> response = new ApiResponse<>();
        response.setResult(this.categoryService.getAllCategoryResponses());
        return response;
    }

    // 2. Lấy chi tiết danh mục theo ID, CÓ KÈM danh sách sản phẩm thuộc danh mục
    // này. Service.getCategoryDetail() đã tự lo việc @Transactional + mapping
    // an toàn, Controller chỉ việc gọi và bọc vào ApiResponse.
    @GetMapping("/{id}")
    public ApiResponse<CategoryDetailResponse> getCategoryById(@PathVariable String id) {
        ApiResponse<CategoryDetailResponse> response = new ApiResponse<>();
        response.setResult(this.categoryService.getCategoryDetail(id));
        return response;
    }

    // 3. Tạo mới danh mục (nhận dữ liệu dạng form-data để hỗ trợ upload ảnh danh
    // mục, giống cách làm với User/Product -> Controller không còn hứng trực
    // tiếp bằng Entity Category nữa, toàn bộ map dữ liệu/validate/xử lý ảnh nằm
    // ở Service)
    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(@Valid @ModelAttribute CategoryCreationRequest request) {
        ApiResponse<CategoryResponse> response = new ApiResponse<>();
        response.setResult(this.categoryService.handleCreateCategory(request));
        return response;
    }

    // Cập nhật danh mục
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable String id,
            @Valid @ModelAttribute CategoryUpdateRequest request) {
        ApiResponse<CategoryResponse> response = new ApiResponse<>();
        response.setResult(this.categoryService.handleUpdateCategory(id, request));
        return response;
    }

    // 5. Xóa danh mục (xóa ảnh trước, giống cách UserRestController xóa avatar
    // trước khi xóa User). Cần Entity thô để đọc tên file ảnh -> dùng
    // categoryService.getCategoryById() (method nội bộ, không phải *Response)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable String id) {
        this.categoryService.deleteCategoryById(id);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Danh mục sản phẩm đã được xóa thành công");
        return response;
    }

    // 6. Xóa hàng loạt danh mục theo danh sách id (body JSON { ids: [...] }).
    // Việc xóa ảnh từng category + xóa record nằm trong 1 transaction ở
    // CategoryService.deleteCategoriesByIds(). Nhờ @SQLDelete ở Category.java,
    // thao tác này là xóa MỀM (UPDATE deleted_at).
    @PostMapping("/bulk-delete")
    public ApiResponse<Void> deleteCategories(@Valid @RequestBody CategoryBulkDeleteRequest request) {
        this.categoryService.deleteCategoriesByIds(request.getIds());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Các danh mục đã được xóa thành công");
        return response;
    }

    // 7. Kích hoạt/khóa hàng loạt danh mục (body JSON { ids: [...], active:
    // true/false })
    @PatchMapping("/bulk-status")
    public ApiResponse<Void> updateCategoriesActive(@Valid @RequestBody CategoryBulkStatusRequest request) {
        this.categoryService.updateCategoriesActive(request.getIds(), request.isActive());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage(request.isActive()
                ? "Các danh mục đã được kích hoạt thành công"
                : "Các danh mục đã được khóa thành công");
        return response;
    }
}
