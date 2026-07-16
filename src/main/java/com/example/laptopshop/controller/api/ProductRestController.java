package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.Product.ProductCreationRequest;
import com.example.laptopshop.dto.request.Product.ProductUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.service.ProductService;
import com.example.laptopshop.service.UploadService;

import jakarta.validation.Valid;

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
    public ApiResponse<List<Product>> getAllProducts() {
        ApiResponse<List<Product>> response = new ApiResponse<>();
        response.setResult(this.productService.getAllProducts());
        return response;
    }

    // 2. Lấy chi tiết sản phẩm theo ID
    @GetMapping("/{id}")
    public ApiResponse<Product> getProductById(@PathVariable long id) {
        ApiResponse<Product> response = new ApiResponse<>();
        response.setResult(this.productService.getProductById(id));
        return response;
    }

    // 3. Tạo mới sản phẩm (nhận dữ liệu dạng form-data để hỗ trợ upload ảnh sản
    // phẩm, giống cách làm với User -> Controller không còn hứng trực tiếp bằng
    // Entity Product nữa, toàn bộ map dữ liệu/validate/xử lý ảnh nằm ở Service)
    @PostMapping
    public ApiResponse<Product> createProduct(
            @Valid @RequestPart("productInfo") ProductCreationRequest request,
            @RequestPart(value = "inputFile", required = false) MultipartFile inputFile) {

        Product savedProduct = this.productService.handleCreateProduct(request, inputFile);
        ApiResponse<Product> response = new ApiResponse<>();
        response.setResult(savedProduct);
        return response;
    }

    // 4. Cập nhật sản phẩm
    @PutMapping("/{id}")
    public ApiResponse<Product> updateProduct(@PathVariable long id,
            @Valid @ModelAttribute ProductUpdateRequest request) {
        Product updatedProduct = this.productService.handleUpdateProduct(id, request);
        ApiResponse<Product> response = new ApiResponse<>();
        response.setResult(updatedProduct);
        return response;
    }

    // 5. Xóa sản phẩm (xóa ảnh trước, giống cách UserRestController xóa avatar
    // trước khi xóa User)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable long id) {
        Product product = this.productService.getProductById(id);

        if (product.getImage() != null) {
            this.uploadService.handleDeleteFile(product.getImage());
        }

        this.productService.deleteProductById(id);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Sản phẩm đã được xóa thành công");
        return response;
    }
}