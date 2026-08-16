package com.example.laptopshop.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.laptopshop.controller.api.ProductRestController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.laptopshop.service.ProductService;
import com.example.laptopshop.service.UploadService;

/**
 * Unit test cho ProductRestController — CHỈ load tầng web (Controller +
 * Jackson + Validation + GlobalExceptionHandler), KHÔNG khởi động toàn bộ
 * Spring Context, KHÔNG chạy code thật trong ProductService/UploadService.
 *
 * @WebMvcTest(ProductRestController.class): chỉ nạp bean liên quan tới
 * Controller này (không nạp ProductService thật, không nạp Repository, không
 * kết nối DB).
 *
 * @AutoConfigureMockMvc(addFilters = false): tắt SecurityFilterChain thật
 * trong lúc test — nếu không tắt, request test sẽ bị JWT filter chặn lại
 * bằng 401 trước khi kịp chạm tới Controller, vì /api/v1/admin/products
 * đang yêu cầu ROLE_ADMIN.
 */
@WebMvcTest(ProductRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductRestControllerTest {

    @Autowired
    private MockMvc mockMvc; // giả lập HTTP request, không cần chạy server thật

    // @MockitoBean: thay ProductService/UploadService thật bằng bản giả
    // (Mockito mock) chỉ tồn tại trong Spring Context riêng của test này.
    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private UploadService uploadService;

    // Giả lập JPA còn thiếu : createdAt và updatedAt
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    // =========================================================================
    // TEST: POST /api/v1/admin/products/bulk-delete
    // =========================================================================

    @Test
    void deleteProducts_validRequest_success() throws Exception {
        // WHEN: gửi body JSON { ids: [...] } hợp lệ (Jackson deserialize thẳng
        // vào ProductBulkDeleteRequest, KHÔNG cần multipart)
        // THEN: response 200 OK + message thành công, service được gọi đúng 1 lần
        mockMvc.perform(post("/api/v1/admin/products/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"id-1\",\"id-2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Các sản phẩm đã được xóa thành công"));

        verify(productService).deleteProductsByIds(List.of("id-1", "id-2"));
    }

    @Test
    void deleteProducts_emptyIds_fail() throws Exception {
        // GIVEN: không cần mock productService vì request sẽ bị chặn ở bước
        // validate (@Valid) TRƯỚC KHI Controller kịp gọi tới Service
        // WHEN: gửi ids rỗng -> vi phạm @NotEmpty(message = "INVALID_PRODUCT_DATA")
        // THEN: GlobalExceptionHandler trả về 400 kèm message
        mockMvc.perform(post("/api/v1/admin/products/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // =========================================================================
    // TEST: PATCH /api/v1/admin/products/bulk-status
    // =========================================================================

    @Test
    void updateProductsActive_validRequest_success() throws Exception {
        // WHEN: gửi body JSON { ids: [...], active: true } hợp lệ
        // THEN: response 200 OK + message kích hoạt, service được gọi đúng 1 lần
        mockMvc.perform(patch("/api/v1/admin/products/bulk-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"id-1\"],\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Các sản phẩm đã được kích hoạt thành công"));

        verify(productService).updateProductsActive(List.of("id-1"), true);
    }

    @Test
    void updateProductsActive_emptyIds_fail() throws Exception {
        // WHEN: gửi ids rỗng -> vi phạm @NotEmpty(message = "INVALID_PRODUCT_DATA")
        // THEN: trả về 400, không gọi tới productService
        mockMvc.perform(patch("/api/v1/admin/products/bulk-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[],\"active\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
