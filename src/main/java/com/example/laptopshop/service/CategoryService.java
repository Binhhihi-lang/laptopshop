package com.example.laptopshop.service;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.dto.request.Category.CategoryCreationRequest;
import com.example.laptopshop.dto.request.Category.CategoryUpdateRequest;
import com.example.laptopshop.dto.response.Category.CategoryDetailResponse;
import com.example.laptopshop.dto.response.Category.CategoryResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.CategoryMapper;
import com.example.laptopshop.repository.CategoryRepository;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class CategoryService {

    CategoryRepository categoryRepository;
    UploadService uploadService;
    CategoryMapper categoryMapper;


    // findById(id) tìm trong DB và trả về Optional<Category>.
    public Category getCategoryById(String id) {
        return this.categoryRepository.findById(id).orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    public void deleteCategoryById(String id) {
        Category category = getCategoryById(id); // kiểm tra tồn tại, nếu không thì throw lỗi
        this.categoryRepository.delete(category);
    }

    // ---- Các method trả Response DTO: LUÔN @Transactional để Hibernate Session
    // còn mở trong lúc MapStruct đọc dữ liệu, tránh LazyInitializationException
    // ----

    // Dùng cho trang danh sách / dropdown: CategoryResponse không có field
    // "products" -> category.getProducts() KHÔNG bị gọi -> không phát sinh
    // thêm câu SQL nào, dù có/không có @Transactional cũng an toàn, nhưng vẫn
    // khai báo readOnly để tối ưu (Hibernate bỏ qua dirty-checking).
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategoryResponses() {
        List<Category> categories = this.categoryRepository.findAll();
        return this.categoryMapper.toResponseList(categories);
    }

    // Dùng cho trang chi tiết: CategoryDetailResponse CÓ field "products" ->
    // category.getProducts() (lazy) BẮT BUỘC phải được Hibernate nạp trong lúc
    // method này còn đang chạy (Transaction/Session còn mở). Nếu mapping việc
    // này được đẩy ra Controller (ngoài @Transactional) thì tùy cấu hình
    // spring.jpa.open-in-view mà có thể dính LazyInitializationException.
    @Transactional(readOnly = true)
    public CategoryDetailResponse getCategoryDetail(String id) {
        Category category = getCategoryById(id);
        return this.categoryMapper.toDetailResponse(category);
    }

    // Nhận DTO từ Controller, validate dữ liệu thô, map sang Entity, xử lý ảnh,
    // lưu DB rồi map luôn sang Response TRONG CÙNG transaction trước khi trả về
    // Controller. Controller không còn hứng trực tiếp bằng Entity Category nữa,
    @Transactional
    public CategoryResponse handleCreateCategory(CategoryCreationRequest request) {
        // 1. Validate dữ liệu thô từ DTO
        String normalizedName = request.getName().trim();
        validateName(normalizedName, null);
        // 2. Map các field thuần (slug, description, displayOrder) từ DTO sang
        // Entity qua MapStruct. name/image KHÔNG được map ở đây (đã ignore trong
        // CategoryMapper) vì cần xử lý riêng bên dưới.
        Category newCategory = this.categoryMapper.toEntity(request);
        newCategory.setName(normalizedName);

        // 3. Xử lý upload ảnh danh mục nếu có
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String image = this.uploadService.handleSaveUploadFile(file, "category");
            newCategory.setImage(image);
        }

        Category saved = this.categoryRepository.save(newCategory);
        return this.categoryMapper.toResponse(saved);
    }

    @Transactional
    public CategoryResponse handleUpdateCategory(String id, CategoryUpdateRequest request) {
        // 1. Tìm Category cũ trong DB, không thấy thì ném lỗi
        Category existingCategory = getCategoryById(id);

        validateName(request.getName(), id);

        // 3. Đổ các field thuần (slug, description, displayOrder, active) từ DTO đè
        // lên Entity cũ qua MapStruct (@MappingTarget), rồi set riêng name bên dưới
        this.categoryMapper.updateEntity(request, existingCategory);
        existingCategory.setName(request.getName().trim());

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

        Category saved = this.categoryRepository.save(existingCategory);
        return this.categoryMapper.toResponse(saved);
    }

    // Validate tên danh mục, nếu trùng với danh mục khác (khác id) thì ném lỗi 2
    // trường hợp: create (currentId = null) và update (currentId = id)
    // kiểu Long
    private void validateName(String name, String currentId) {
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
