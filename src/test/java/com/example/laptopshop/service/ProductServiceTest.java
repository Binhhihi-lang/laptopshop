package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.laptopshop.domain.Product;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.ProductMapper;
import com.example.laptopshop.repository.ProductRepository;

/**
 * @ExtendWith(MockitoExtension.class): Chỉ thị cho JUnit 5 bật tính năng Mockito.
 * KHÔNG dùng @SpringBootTest, bài test này hoàn toàn chạy bằng Java thuần, không
 * khởi động Spring Boot.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    // --- 1. KHAI BÁO CÁC DEPENDENCY GIẢ (MOCKS) ---
    // ProductService thật đang cần 4 dependency này, ta dùng @Mock để tạo bản giả cho chúng.
    @Mock
    private CategoryService categoryService;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UploadService uploadService;
    @Mock
    private ProductMapper productMapper;

    // --- 2. KHAI BÁO CLASS CẦN TEST ---
    // @InjectMocks tự động lấy các @Mock ở trên nhét vào constructor của ProductService
    @InjectMocks
    private ProductService productService;

    private Product productWithImage;
    private Product productWithoutImage;

    @BeforeEach
    void setUp() {
        productWithImage = new Product();
        productWithImage.setId("id-1");
        productWithImage.setImage("product-1.jpg");
        productWithImage.setActive(false);

        productWithoutImage = new Product();
        productWithoutImage.setId("id-2");
        productWithoutImage.setImage(null);
    }

    // =========================================================================
    // TEST METHOD: deleteProductsByIds()
    // =========================================================================

    @Test
    void deleteProductsByIds_allFound_deleteImagesAndRecords() {
        // GIVEN: cả 2 id đều tồn tại -> findAllById trả về đủ 2 sản phẩm (có ảnh)
        when(productRepository.findAllById(List.of("id-1", "id-2")))
                .thenReturn(List.of(productWithImage, productWithoutImage));

        // WHEN: xóa hàng loạt 2 sản phẩm
        productService.deleteProductsByIds(List.of("id-1", "id-2"));

        // THEN: ảnh của sản phẩm có ảnh được xóa, sản phẩm không ảnh bỏ qua,
        // và toàn bộ bản ghi được xóa
        verify(uploadService).handleDeleteFile("product-1.jpg");
        verify(uploadService, never()).handleDeleteFile(null);
        verify(productRepository).deleteAll(List.of(productWithImage, productWithoutImage));
    }

    @Test
    void deleteProductsByIds_productNotFound_throwException() {
        // GIVEN: chỉ 1 trong 2 id tồn tại -> findAllById trả về thiếu 1 sản phẩm
        when(productRepository.findAllById(List.of("id-1", "id-2")))
                .thenReturn(List.of(productWithImage));

        // WHEN & THEN: ném AppException với mã PRODUCT_NOT_FOUND, không xóa gì cả
        AppException exception = assertThrows(AppException.class,
                () -> productService.deleteProductsByIds(List.of("id-1", "id-2")));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
        verify(uploadService, never()).handleDeleteFile(anyString());
        verify(productRepository, never()).deleteAll(any());
    }

    // =========================================================================
    // TEST METHOD: updateProductsActive()
    // =========================================================================

    @Test
    void updateProductsActive_allFound_setActiveAndSave() {
        // GIVEN: id tồn tại -> findAllById trả về sản phẩm đang ở trạng thái inactive
        when(productRepository.findAllById(List.of("id-1")))
                .thenReturn(List.of(productWithImage));

        // WHEN: kích hoạt hàng loạt
        productService.updateProductsActive(List.of("id-1"), true);

        // THEN: active được set true và lưu lại DB
        assertTrue(productWithImage.isActive());
        verify(productRepository).saveAll(List.of(productWithImage));
    }

    @Test
    void updateProductsActive_productNotFound_throwException() {
        // GIVEN: id không tồn tại -> findAllById trả về danh sách rỗng
        when(productRepository.findAllById(List.of("missing-id"))).thenReturn(List.of());

        // WHEN & THEN: ném AppException với mã PRODUCT_NOT_FOUND, không lưu gì cả
        AppException exception = assertThrows(AppException.class,
                () -> productService.updateProductsActive(List.of("missing-id"), true));

        assertEquals(ErrorCode.PRODUCT_NOT_FOUND, exception.getErrorCode());
        verify(productRepository, never()).saveAll(any());
    }
}
