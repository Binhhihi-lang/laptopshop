package com.example.laptopshop.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.dto.request.Category.CategoryCreationRequest;
import com.example.laptopshop.dto.request.Category.CategoryUpdateRequest;
import com.example.laptopshop.dto.response.Category.CategoryDetailResponse;
import com.example.laptopshop.dto.response.Category.CategoryResponse;

// uses = ProductMapper.class: khi map field "products" (List<Product> ->
// List<ProductResponse>) trong toDetailResponse(), MapStruct tự tìm và gọi
// ProductMapper.toResponse() cho từng phần tử thay vì tự chế sinh logic map
// riêng -> tái dùng đúng 1 chỗ định nghĩa Product -> ProductResponse.
@Mapper(componentModel = "spring", uses = ProductMapper.class)
public interface CategoryMapper {

    // Bỏ qua các field cần xử lý riêng trong Service:
    // - id: do JPA tự sinh
    // - name: cần trim() trước khi set (Service đang tự làm)
    // - image: cần upload file trước rồi mới có tên file để set
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "image", ignore = true)
    Category toEntity(CategoryCreationRequest request);

    // @MappingTarget: đổ dữ liệu mới từ DTO ĐÈ LÊN Entity cũ đã có sẵn
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "image", ignore = true)
    void updateEntity(CategoryUpdateRequest request, @MappingTarget Category entity);

    // Dùng cho trang danh sách / dropdown: KHÔNG có field "products" -> MapStruct
    // KHÔNG gọi category.getProducts() -> Hibernate KHÔNG chạy thêm câu SQL nào
    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponseList(List<Category> categories);

    // Dùng cho trang chi tiết danh mục : CÓ field "products" -> MapStruct sẽ gọi
    // category.getProducts() không chứa id của Category nên không bị gọi lại. PHẢI
    // được gọi bên trong một method
    // @Transactional của Service (CategoryService.getCategoryDetail), nếu không
    // sẽ dính LazyInitializationException vì Session đã đóng.
    CategoryDetailResponse toDetailResponse(Category category);
}
