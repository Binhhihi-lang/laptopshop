package com.example.laptopshop.controller.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Product;
import com.example.laptopshop.service.ProductService;
import com.example.laptopshop.service.UploadService;

@RestController
@RequestMapping("/api/v1/products")
public class ProductRestController {

    private final ProductService productService;
    private final UploadService uploadService;

    public ProductRestController(ProductService productService, UploadService uploadService) {
        this.productService = productService;
        this.uploadService = uploadService;
    }

    // 1. Lấy danh sách sản phẩm
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

    // 3. Tạo mới sản phẩm (Hỗ trợ upload ảnh sản phẩm)
    @PostMapping
    public ResponseEntity<Product> createProduct(
            @ModelAttribute Product newProduct,
            @RequestParam(value = "inputFile", required = false) MultipartFile file) {

        if (file != null && !file.isEmpty()) {
            String image = this.uploadService.handleSaveUploadFile(file, "Product");
            newProduct.setImage(image);
        }

        Product savedProduct = this.productService.handleSaveProduct(newProduct);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProduct);
    }

    // 4. Cập nhật sản phẩm
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(
            @PathVariable long id,
            @ModelAttribute Product productUpdate,
            @RequestParam(value = "inputFile", required = false) MultipartFile file) {

        Product currentProduct = this.productService.getProductById(id);
        if (currentProduct == null) {
            return ResponseEntity.notFound().build();
        }

        // Cập nhật thông tin
        currentProduct.setName(productUpdate.getName());
        currentProduct.setPrice(productUpdate.getPrice());
        currentProduct.setDetailDesc(productUpdate.getDetailDesc());
        currentProduct.setShortDesc(productUpdate.getShortDesc());
        currentProduct.setQuantity(productUpdate.getQuantity());
        currentProduct.setSold(productUpdate.getSold());
        currentProduct.setFactory(productUpdate.getFactory());
        currentProduct.setTarget(productUpdate.getTarget());

        // Upload ảnh mới nếu có
        if (file != null && !file.isEmpty()) {
            if (currentProduct.getImage() != null) {
                this.uploadService.handleDeleteFile(currentProduct.getImage(), "Product");
            }
            String newImage = this.uploadService.handleSaveUploadFile(file, "Product");
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
            this.uploadService.handleDeleteFile(product.getImage(), "Product");
        }

        this.productService.deleteProductById(id);
        return ResponseEntity.noContent().build();
    }
}
