package com.example.laptopshop.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.Product.ProductCreationRequest;
import com.example.laptopshop.dto.request.Product.ProductUpdateRequest;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.ProductRepository;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final UploadService uploadService;

    public ProductService(ProductRepository productRepository, CategoryService categoryService,
            UploadService uploadService) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.uploadService = uploadService;
    }

    public List<Product> getAllProducts() {
        return this.productRepository.findAll();
    }

    public Product getProductById(long id) {
        Product product = this.productRepository.findById(id);
        if (product == null) {
            throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        return product;
    }

    public void deleteProductById(long id) {
        Product product = getProductById(id); // kiểm tra tồn tại, không thì throw lỗi
        this.productRepository.delete(product);
    }

    // Nhận DTO từ Controller, validate dữ liệu thô, map sang Entity, xử lý ảnh và
    // gán Category, rồi lưu DB. Controller không còn hứng trực tiếp bằng Entity
    // Product nữa, giống cách làm với User.
    public Product handleCreateProduct(ProductCreationRequest request) {
        // 1. Validate dữ liệu thô từ DTO
        validateCode(request.getCode(), null);
        validateName(request.getName());
        validatePrice(request.getPrice());
        Category category = validateAndGetCategory(request.getCategoryId());

        // 2. Map dữ liệu từ DTO sang Entity Product
        Product newProduct = new Product();
        newProduct.setCode(request.getCode().trim().toUpperCase());
        newProduct.setName(request.getName().trim());
        newProduct.setPrice(request.getPrice());
        newProduct.setShortDesc(request.getShortDesc());
        newProduct.setDetailDesc(request.getDetailDesc());
        newProduct.setQuantity(request.getQuantity() == null ? 0 : request.getQuantity());
        newProduct.setFactory(request.getFactory());
        newProduct.setTarget(request.getTarget());
        newProduct.setCpu(request.getCpu());
        newProduct.setRam(request.getRam());
        newProduct.setStorage(request.getStorage());
        newProduct.setGpu(request.getGpu());
        newProduct.setScreen(request.getScreen());
        newProduct.setOs(request.getOs());
        newProduct.setWeight(request.getWeight());
        newProduct.setWarrantyMonths(request.getWarrantyMonths());
        newProduct.setCategory(category);

        // Sản phẩm mới tạo luôn bắt đầu từ 0 lượt bán, không cho client tự set qua form
        // create
        newProduct.setSold(0);

        // 3. Xử lý upload ảnh sản phẩm nếu có
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String image = this.uploadService.handleSaveUploadFile(file, "product");
            newProduct.setImage(image);
        }

        return this.productRepository.save(newProduct);
    }

    public Product handleUpdateProduct(long id, ProductUpdateRequest request) {
        // 1. Tìm Product cũ trong DB, không thấy thì ném lỗi
        Product currentProduct = getProductById(id);

        // Validate dữ liệu
        validateCode(request.getCode(), id);
        validateName(request.getName());
        validatePrice(request.getPrice());
        Category category = validateAndGetCategory(request.getCategoryId());

        // 3. Đổ dữ liệu mới từ DTO đè lên Entity cũ
        currentProduct.setCode(request.getCode().trim().toUpperCase());
        currentProduct.setName(request.getName().trim());
        currentProduct.setPrice(request.getPrice());
        currentProduct.setShortDesc(request.getShortDesc());
        currentProduct.setDetailDesc(request.getDetailDesc());
        currentProduct.setQuantity(request.getQuantity() == null ? 0 : request.getQuantity());
        currentProduct.setSold(request.getSold() == null ? currentProduct.getSold() : request.getSold());
        currentProduct.setFactory(request.getFactory());
        currentProduct.setTarget(request.getTarget());
        currentProduct.setCpu(request.getCpu());
        currentProduct.setRam(request.getRam());
        currentProduct.setStorage(request.getStorage());
        currentProduct.setGpu(request.getGpu());
        currentProduct.setScreen(request.getScreen());
        currentProduct.setOs(request.getOs());
        currentProduct.setWeight(request.getWeight());
        currentProduct.setWarrantyMonths(request.getWarrantyMonths());
        currentProduct.setActive(request.isActive());
        currentProduct.setCategory(category);

        // 4. Xử lý đổi ảnh mới nếu admin gửi lên file mới, xóa ảnh cũ trước khi lưu ảnh
        // mới
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            if (currentProduct.getImage() != null) {
                this.uploadService.handleDeleteFile(currentProduct.getImage());
            }
            String newImage = this.uploadService.handleSaveUploadFile(file, "product");
            currentProduct.setImage(newImage);
        }

        return this.productRepository.save(currentProduct);
    }

    private void validateCode(String code, Long currentId) {
        if (code == null || code.isBlank()) {
            throw new AppException(ErrorCode.PRODUCT_CODE_REQUIRED);
        }

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

    private Category validateAndGetCategory(Long categoryId) {
        if (categoryId == null || categoryId <= 0) {
            throw new AppException(ErrorCode.PRODUCT_CATEGORY_REQUIRED);
        }
        // getCategoryById tự ném AppException(CATEGORY_NOT_FOUND) nếu không tồn tại
        return this.categoryService.getCategoryById(categoryId);
    }
}