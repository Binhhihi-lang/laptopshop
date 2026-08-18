package com.example.laptopshop.service;

import java.util.List;

import com.example.laptopshop.domain.Category;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.Product.ProductCreationRequest;
import com.example.laptopshop.dto.request.Product.ProductUpdateRequest;
import com.example.laptopshop.dto.response.Product.ProductResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.ProductMapper;
import com.example.laptopshop.repository.ProductRepository;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Service
public class ProductService {
     CategoryService categoryService;
     ProductRepository productRepository;
     UploadService uploadService;
     ProductMapper productMapper;

    public Product getProductById(String id) {
        return this.productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
    }

    // Xóa MỀM: gọi y hệt như xóa thật trước đây, nhưng nhờ @SQLDelete khai báo
    // ở Product.java, Hibernate tự động đổi câu lệnh thành UPDATE deleted_at =
    // NOW() thay vì DELETE thật
    public void deleteProductById(String id) {
        Product product = getProductById(id); // kiểm tra tồn tại, không thì throw lỗi

        if (product.getImage() != null) {
            this.uploadService.handleDeleteFile(product.getImage());
        }
        this.productRepository.delete(product);
    }

    // Xóa hàng loạt sản phẩm theo danh sách id: xóa ảnh của từng sản phẩm trước
    // khi xóa bản ghi (giống deleteProduct đơn), wrap trong 1 transaction để các
    // bước xóa ảnh + xóa record nhất quán với nhau. Tương tự deleteProductById,
    // deleteAll() cũng bị @SQLDelete đổi thành xóa MỀM (UPDATE deleted_at).
    @Transactional
    public void deleteProductsByIds(List<String> ids) {
        List<Product> products = this.productRepository.findAllById(ids);
        if (products.size() != ids.size()) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        for (Product product : products) {
            if (product.getImage() != null) {
                this.uploadService.handleDeleteFile(product.getImage());
            }
        }
        this.productRepository.deleteAll(products);
    }

    // Kích hoạt/khóa hàng loạt sản phẩm theo danh sách id
    @Transactional
    public void updateProductsActive(List<String> ids, boolean active) {
        List<Product> products = this.productRepository.findAllById(ids);
        if (products.size() != ids.size()) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        products.forEach(product -> product.setActive(active));
        this.productRepository.saveAll(products);
    }

    // ---- Các method trả Response DTO: LUÔN @Transactional để Hibernate Session
    // còn mở trong lúc MapStruct đọc product.getCategory() (quan hệ @ManyToOne),
    // tránh LazyInitializationException nếu Category được khai báo FetchType.LAZY
    // ----

    @Transactional(readOnly = true)
    public List<ProductResponse> getAllProductResponses() {
        List<Product> products = this.productRepository.findAll();
        return this.productMapper.toResponseList(products);
    }

    @Transactional(readOnly = true)
    public ProductResponse getProductResponseById(String id) {
        Product product = getProductById(id);
        return this.productMapper.toResponse(product);
    }

    // Nhận DTO từ Controller, validate dữ liệu thô, map sang Entity, xử lý ảnh và
    // gán Category, lưu DB rồi map luôn sang Response TRONG CÙNG transaction
    // trước khi trả về Controller. Controller không còn hứng trực tiếp bằng
    // Entity Product nữa, giống cách làm với User.
    @Transactional
    public ProductResponse handleCreateProduct(ProductCreationRequest request, MultipartFile file) {
        // 1. Validate dữ liệu thô từ DTO
        validateCode(request.getCode(), null);
        validateName(request.getName());
        validatePrice(request.getPrice());

        // 2. Map các field thuần (price, shortDesc, detailDesc, factory, target,
        // cpu, ram, storage, gpu, screen, os, weight, warrantyMonths) từ DTO sang
        // Entity qua MapStruct. code/name/quantity/sold/category/image KHÔNG được
        // map ở đây (đã ignore trong ProductMapper) vì cần xử lý riêng bên dưới.
        Product newProduct = this.productMapper.toEntity(request);
        newProduct.setCode(request.getCode().trim().toUpperCase());
        newProduct.setName(request.getName().trim());
        newProduct.setQuantity(request.getQuantity() == null ? 0 : request.getQuantity());

        Category category = categoryService.getCategoryById(request.getCategoryId());
        newProduct.setCategory(category);

        // Sản phẩm mới tạo luôn bắt đầu từ 0 lượt bán, không cho client tự set qua form
        // create
        newProduct.setSold(0);

        // 3. Xử lý upload ảnh sản phẩm nếu có
        if (file != null && !file.isEmpty()) {
            String image = this.uploadService.handleSaveUploadFile(file, "product");
            newProduct.setImage(image);
        }

        Product saved = this.productRepository.save(newProduct);
        return this.productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse handleUpdateProduct(String id, ProductUpdateRequest request, MultipartFile file) {
        // 1. Tìm Product cũ trong DB, không thấy thì ném lỗi
        Product currentProduct = getProductById(id);

        // Validate dữ liệu
        validateCode(request.getCode(), id);
        validateName(request.getName());
        validatePrice(request.getPrice());

        // 3. Đổ các field thuần (price, mô tả, thông số kỹ thuật, active) từ DTO đè
        // lên Entity cũ qua MapStruct (@MappingTarget), rồi set riêng
        // code/name/quantity/sold/category bên dưới
        this.productMapper.updateEntity(request, currentProduct);
        currentProduct.setCode(request.getCode().trim().toUpperCase());
        currentProduct.setName(request.getName().trim());
        currentProduct.setQuantity(request.getQuantity() == null ? 0 : request.getQuantity());
        Category category = this.categoryService.getCategoryById(request.getCategoryId());
        currentProduct.setCategory(category);

        // 4. Xử lý ảnh: ưu tiên file mới; nếu không có file mới và có cờ xóa thì xóa ảnh cũ;
        // còn lại giữ nguyên ảnh hiện tại
        boolean hasNewFile = file != null && !file.isEmpty();
        if (hasNewFile) {
            if (currentProduct.getImage() != null) {
                this.uploadService.handleDeleteFile(currentProduct.getImage());
            }
            String newImage = this.uploadService.handleSaveUploadFile(file, "product");
            currentProduct.setImage(newImage);
        } else if (request.isRemoveImage() && currentProduct.getImage() != null) { // có cờ xóa và có ảnh cũ
            this.uploadService.handleDeleteFile(currentProduct.getImage());
            currentProduct.setImage(null);
        }

        Product saved = this.productRepository.save(currentProduct);
        return this.productMapper.toResponse(saved);
    }

    private void validateCode(String code, String currentId) {

        String normalized = code.trim();
        boolean exists = currentId == null
                ? this.productRepository.existsByCodeIgnoreCase(normalized)
                : this.productRepository.existsByCodeIgnoreCaseAndIdNot(normalized, currentId);

        if (exists) {
            throw new AppException(ErrorCode.PRODUCT_ALREADY_EXISTS);
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new AppException(ErrorCode.PRODUCT_NAME_EMPTY);
        }
    }

    private void validatePrice(Long price) {
        if (price == null || price <= 0) {
            throw new AppException(ErrorCode.PRODUCT_PRICE_INVALID);
        }
    }

}
