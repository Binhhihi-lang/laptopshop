package com.example.laptopshop.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.Product.ProductCreationRequest;
import com.example.laptopshop.dto.request.Product.ProductUpdateRequest;
import com.example.laptopshop.dto.response.Product.ProductResponse;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    // Bỏ qua các field cần xử lý riêng trong Service (giữ NGUYÊN logic tay đang
    // có, chỉ MapStruct hóa phần field copy thuần túy: price, shortDesc,
    // detailDesc, factory, target, cpu, ram, storage, gpu, screen, os, weight,
    // warrantyMonths):
    // - id: do JPA tự sinh
    // - code: cần trim().toUpperCase()
    // - name: cần trim()
    // - quantity: cần normalize (null -> 0), không map thẳng
    // - sold: tạo mới luôn = 0, update thì null -> giữ giá trị cũ (không map thẳng)
    // - category: Service đang gán trực tiếp từ request.getCategory(), giữ nguyên
    // cách làm hiện tại thay vì để MapStruct tự sinh sub-mapping
    // - image: cần upload file trước rồi mới có tên file để set
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "quantity", ignore = true)
    @Mapping(target = "sold", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(ProductCreationRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "code", ignore = true)
    @Mapping(target = "name", ignore = true)
    @Mapping(target = "quantity", ignore = true)
    @Mapping(target = "sold", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "image", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ProductUpdateRequest request, @MappingTarget Product entity);

    // Entity -> Response: category rút gọn thành categoryId/categoryName (tránh
    // trả nguyên object Category lồng bên trong, tránh lazy-loading exception)
    @Mapping(target = "categoryId", source = "category.id")
    @Mapping(target = "categoryName", source = "category.name")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponseList(List<Product> products);
}
