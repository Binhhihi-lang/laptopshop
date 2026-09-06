package com.example.laptopshop.controller.api.client;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Product.ProductResponse;
import com.example.laptopshop.service.ProductService;

/**
 * Storefront: duyệt sản phẩm công khai.
 * Đường dẫn: /api/v1/client/products
 * Bảo mật: permitAll() ở tầng SecurityConfiguration.
 * Quy tắc storefront: chỉ trả product active VÀ category active (đã chốt ở query BE).
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/client/products")
public class ClientProductController {

    ProductService productService;

    // 1. Danh sách sản phẩm phân trang, có filter + sort.
    // page, size, sort -> Pageable mặc định (Spring tự map từ query string).
    // Mặc định 12 sp/trang, sort theo createdAt DESC nếu FE không truyền sort.
    @GetMapping
    public ApiResponse<Page<ProductResponse>> searchProducts(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String factory,
            @RequestParam(required = false) Long minPrice,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 12, sort = "createdAt") Pageable pageable) {
        Page<ProductResponse> result = this.productService.searchStorefront(
                categoryId, factory, minPrice, maxPrice, keyword, pageable);
        ApiResponse<Page<ProductResponse>> response = new ApiResponse<>();
        response.setResult(result);
        return response;
    }

    // 2. Chi tiết sản phẩm theo CODE (SKU) — URL thân thiện SEO.
    // Đồng thời trả về related (cùng category, tối đa 8).
    // Dùng ProductDetailResponse riêng để gói cả detail + related (FE tiện dùng 1 lần).
    @GetMapping("/{code}")
    public ApiResponse<ProductDetailResponse> getProductDetail(@PathVariable String code) {
        ProductResponse detail = this.productService.getProductResponseByCode(code);
        List<ProductResponse> related = this.productService.getRelatedProducts(
                detail.getCategoryId(), detail.getId(), 8);
        ApiResponse<ProductDetailResponse> response = new ApiResponse<>();
        response.setResult(new ProductDetailResponse(detail, related));
        return response;
    }

    // 3. Danh sách hãng (factory) duy nhất — dùng cho bộ lọc "Hãng" ở FE.
    @GetMapping("/brands")
    public ApiResponse<List<String>> getActiveBrands() {
        ApiResponse<List<String>> response = new ApiResponse<>();
        response.setResult(this.productService.getActiveFactoryNames());
        return response;
    }

    /**
     * Gói response cho trang chi tiết sản phẩm: thông tin sản phẩm + danh sách
     * sản phẩm liên quan. Tách riêng (không tái sử dụng ProductResponse) để
     * tránh phình response
     */
    public record ProductDetailResponse(ProductResponse product, List<ProductResponse> related) {
    }
}
