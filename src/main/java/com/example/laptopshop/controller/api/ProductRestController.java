package com.example.laptopshop.controller.api;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.request.Product.ProductBulkDeleteRequest;
import com.example.laptopshop.dto.request.Product.ProductBulkStatusRequest;
import com.example.laptopshop.dto.request.Product.ProductCreationRequest;
import com.example.laptopshop.dto.request.Product.ProductUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Product.ProductResponse;
import com.example.laptopshop.service.ProductService;
import com.example.laptopshop.service.UploadService;

import jakarta.validation.Valid;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/admin/products")
public class ProductRestController {

    ProductService productService;
    UploadService uploadService;


    // 1. Lấy danh sách sản phẩm
    @GetMapping
    public ApiResponse<List<ProductResponse>> getAllProducts() {
        ApiResponse<List<ProductResponse>> response = new ApiResponse<>();
        response.setResult(this.productService.getAllProductResponses());
        return response;
    }

    // 2. Lấy chi tiết sản phẩm theo ID
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getProductById(@PathVariable String id) {
        ApiResponse<ProductResponse> response = new ApiResponse<>();
        response.setResult(this.productService.getProductResponseById(id));
        return response;
    }

    // 3. Tạo mới sản phẩm (nhận dữ liệu dạng form-data để hỗ trợ upload ảnh sản
    // phẩm, giống cách làm với User -> Controller không còn hứng trực tiếp bằng
    // Entity Product nữa, toàn bộ map dữ liệu/validate/xử lý ảnh nằm ở Service)
    @PostMapping

    public ApiResponse<ProductResponse> createProduct(
            @Valid @RequestPart("productInfo") ProductCreationRequest request,
            @RequestPart(value = "inputFile", required = false) MultipartFile inputFile) {

        ApiResponse<ProductResponse> response = new ApiResponse<>();
        response.setResult(this.productService.handleCreateProduct(request, inputFile));
        return response;
    }

    // 4. Cập nhật sản phẩm (cùng multipart với create: productInfo + inputFile)
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> updateProduct(
            @PathVariable String id,
            @Valid @RequestPart("productInfo") ProductUpdateRequest request,
            @RequestPart(value = "inputFile", required = false) MultipartFile inputFile) {
        ApiResponse<ProductResponse> response = new ApiResponse<>();
        response.setResult(this.productService.handleUpdateProduct(id, request, inputFile));
        return response;
    }

    // 5. Xóa sản phẩm (xóa ảnh trước, giống cách UserRestController xóa avatar
    // trước khi xóa User). Cần Entity thô để đọc tên file ảnh -> dùng
    // productService.getProductById() (method nội bộ, không phải *Response)
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteProduct(@PathVariable String id) {
        this.productService.deleteProductById(id);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Sản phẩm đã được xóa thành công");
        return response;
    }

    // 6. Xóa hàng loạt sản phẩm theo danh sách id (body JSON { ids: [...] }).
    // Việc xóa ảnh từng sản phẩm + xóa record nằm trong 1 transaction ở
    // ProductService.deleteProductsByIds() để đảm bảo nhất quán.
    @PostMapping("/bulk-delete")
    public ApiResponse<Void> deleteProducts(
            @Valid @RequestBody ProductBulkDeleteRequest request) {
        this.productService.deleteProductsByIds(request.getIds());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Các sản phẩm đã được xóa thành công");
        return response;
    }

    // 7. Kích hoạt/khóa hàng loạt sản phẩm (body JSON { ids: [...], active:
    // true/false })
    @PatchMapping("/bulk-status")
    public ApiResponse<Void> updateProductsActive(
            @Valid @RequestBody ProductBulkStatusRequest request) {
        this.productService.updateProductsActive(request.getIds(), request.isActive());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage(request.isActive()
                ? "Các sản phẩm đã được kích hoạt thành công"
                : "Các sản phẩm đã được khóa thành công");
        return response;
    }
}
