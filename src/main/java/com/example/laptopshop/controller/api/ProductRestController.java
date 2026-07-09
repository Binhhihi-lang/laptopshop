package com.example.laptopshop.controller.api;

import java.util.List;

import org.aspectj.weaver.ast.Not;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.domain.Product;
import com.example.laptopshop.service.CategoryService;
import com.example.laptopshop.service.ProductService;
import com.example.laptopshop.service.UploadService;

@RestController
@RequestMapping("/api/v1/products")
public class ProductRestController {

    private final ProductService productService;
    private final UploadService uploadService;
    private final CategoryService categoryService;

    public ProductRestController(ProductService productService, UploadService uploadService,
            CategoryService categoryService) {
        this.productService = productService;
        this.uploadService = uploadService;
        this.categoryService = categoryService;
    }

    // 1. Lấy danh sách sản phẩm
    // ResponseEntity (kèm HTTP status code: 200 OK, 201 Created, 404 Not Found...).

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(this.productService.getAllProducts());
    }

    // 2. Lấy chi tiết sản phẩm theo ID
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable long id) {
        Product product = this.productService.getProductById(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(product);
    }

    // 3. Tạo mới sản phẩm (hỗ trợ upload ảnh sản phẩm)
    @PostMapping
    public ResponseEntity<Product> createProduct(
            // lấy dữ liệu từ form-data (multipart/form-data) -> @ModelAttribute
            // cách 2 để sử dụng @RequestPart để lấy dữ liệu JSON từ form-data để không bị
            // tràn bộ nhớ khi có nhiều trường text
            @RequestPart("productInfo") Product newProduct,
            @RequestParam(value = "inputFile", required = false) MultipartFile file) {

        if (file != null && !file.isEmpty()) {
            String image = this.uploadService.handleSaveUploadFile(file, "product");
            newProduct.setImage(image);
        }

        // Form gửi "category.id" -> Spring tự dựng Category rỗng chỉ có id,
        // cần lấy lại Category thật từ DB trước khi lưu (giống cách xử lý role cho
        // User
        if (newProduct.getCategory() != null && newProduct.getCategory().getId() > 0) {
            Category category = this.categoryService.getCategoryById(newProduct.getCategory().getId());
            newProduct.setCategory(category);
        }

        Product savedProduct = this.productService.handleSaveProduct(newProduct);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProduct);
    }

    // 4. Cập nhật sản phẩm
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable long id,
            @RequestPart("productInfo") Product productUpdate,
            @RequestParam(value = "inputFile", required = false) MultipartFile file) {

        Product currentProduct = this.productService.getProductById(id);
        if (currentProduct == null) {
            return ResponseEntity.notFound().build();
        }

        // Cập nhật thông tin
        currentProduct.setCode(productUpdate.getCode());
        currentProduct.setName(productUpdate.getName());
        currentProduct.setPrice(productUpdate.getPrice());
        currentProduct.setDetailDesc(productUpdate.getDetailDesc());
        currentProduct.setShortDesc(productUpdate.getShortDesc());
        currentProduct.setQuantity(productUpdate.getQuantity());
        currentProduct.setSold(productUpdate.getSold());
        currentProduct.setFactory(productUpdate.getFactory());
        currentProduct.setTarget(productUpdate.getTarget());
        currentProduct.setCpu(productUpdate.getCpu());
        currentProduct.setRam(productUpdate.getRam());
        currentProduct.setStorage(productUpdate.getStorage());
        currentProduct.setGpu(productUpdate.getGpu());
        currentProduct.setScreen(productUpdate.getScreen());
        currentProduct.setOs(productUpdate.getOs());
        currentProduct.setWeight(productUpdate.getWeight());
        currentProduct.setWarrantyMonths(productUpdate.getWarrantyMonths());
        currentProduct.setActive(productUpdate.isActive());

        if (productUpdate.getCategory() != null && productUpdate.getCategory().getId() > 0) {
            Category category = this.categoryService.getCategoryById(productUpdate.getCategory().getId());
            currentProduct.setCategory(category);
        }

        // Upload ảnh mới nếu có
        if (file != null && !file.isEmpty()) {
            if (currentProduct.getImage() != null) {
                this.uploadService.handleDeleteFile(currentProduct.getImage(), "product");
            }
            String newImage = this.uploadService.handleSaveUploadFile(file, "product");
            currentProduct.setImage(newImage);
        }

        Product updatedProduct = this.productService.handleSaveProduct(currentProduct);
        return ResponseEntity.ok(updatedProduct);
    }

    // 5. Xóa sản phẩm
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable long id) {
        Product product = this.productService.getProductById(id);
        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        if (product.getImage() != null) {
            this.uploadService.handleDeleteFile(product.getImage(), "product");
        }

        this.productService.deleteProductById(id);
        return ResponseEntity.noContent().build();
    }
}