package com.example.laptopshop.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.laptopshop.controller.api.UserRestController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.response.User.UserResponse;
import com.example.laptopshop.service.UploadService;
import com.example.laptopshop.service.UserService;

/**
 * Unit test cho UserRestController — CHỈ load tầng web (Controller +
 * Jackson + Validation + GlobalExceptionHandler), KHÔNG khởi động toàn bộ
 * Spring Context, KHÔNG chạy code thật trong UserService/UploadService.
 *
 * @WebMvcTest(UserRestController.class): chỉ nạp bean liên quan tới
 * Controller này (không nạp UserService thật, không nạp Repository, không
 * kết nối DB).
 *
 * @AutoConfigureMockMvc(addFilters = false): tắt SecurityFilterChain thật
 * trong lúc test — nếu không tắt, request test sẽ bị JWT filter chặn lại
 * bằng 401 trước khi kịp chạm tới Controller, vì /api/v1/admin/users đang
 * yêu cầu ROLE_ADMIN.
 */
@WebMvcTest(UserRestController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserRestControllerTest {

    @Autowired
    private MockMvc mockMvc; // giả lập HTTP request, không cần chạy server thật

    // @MockBean: thay UserService/UploadService thật bằng bản giả (Mockito
    // mock) chỉ tồn tại trong Spring Context riêng của test này. Controller
    // gọi userService.handleCreateUser(...) sẽ nhận đúng object mình khai báo
    // sẵn ở phần "GIVEN" của từng test, không chạy logic thật (không hash
    // password, không lưu DB...).
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UploadService uploadService;

    // Giả lập JPA còn thiếu : createdAt và updatedAt
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private UserResponse userResponse;

    // id kiểu String (UUID) khớp đúng User.java thật (@GeneratedValue(strategy
    // = GenerationType.UUID)), KHÔNG phải long như bản nháp trước.
    private static final String SAMPLE_USER_ID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";

    @BeforeEach // chạy lại trước MỖI @Test -> đảm bảo các test độc lập nhau
    void initData() {
        userResponse = new UserResponse();
        userResponse.setId(SAMPLE_USER_ID);
        userResponse.setEmail("john@example.com");
        userResponse.setFullName("John Doe");
        userResponse.setPhone("0900000000");
        userResponse.setAddress("Hà Nội");
        userResponse.setRoleNames(List.of("USER"));
    }

    @Test
    void createUser_validRequest_success() throws Exception {
        // GIVEN: bất kỳ UserCreationRequest hợp lệ nào gửi vào
        // handleCreateUser() cũng cho trả về userResponse đã chuẩn bị sẵn ở
        // initData()
        when(userService.handleCreateUser(any(UserCreationRequest.class)))
                .thenReturn(userResponse);

        // WHEN: gọi POST /api/v1/admin/users dạng multipart/form-data (vì
        // Controller nhận @ModelAttribute UserCreationRequest, không phải
        // @RequestBody JSON, do cần hỗ trợ upload avatar cùng lúc)
        // THEN: response 200 OK, body đúng cấu trúc ApiResponse { result: {...} }
        mockMvc.perform(multipart("/api/v1/admin/users")
                        .param("email", "john@example.com")
                        .param("password", "12345678")
                        .param("fullName", "John Doe")
                        .param("phone", "0900000000")
                        .param("address", "Hà Nội")
                        .param("roleNames", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.id").value(SAMPLE_USER_ID))
                .andExpect(jsonPath("$.result.email").value("john@example.com"))
                .andExpect(jsonPath("$.result.fullName").value("John Doe"))
                .andExpect(jsonPath("$.result.roleNames[0]").value("USER"));
    }

    @Test
    void createUser_emailBlank_fail() throws Exception {
        // GIVEN: không cần mock userService vì request sẽ bị chặn lại ở bước
        // validate (@Valid) TRƯỚC KHI Controller kịp gọi tới Service

        // WHEN: gửi email rỗng -> vi phạm @NotBlank(message = "USER_EMAIL_EMPTY")
        // trong UserCreationRequest
        // THEN: GlobalExceptionHandler bắt MethodArgumentNotValidException/
        // BindException, trả về 400 kèm message tương ứng ErrorCode.USER_EMAIL_EMPTY
        mockMvc.perform(multipart("/api/v1/admin/users")
                        .param("email", "")
                        .param("password", "12345678")
                        .param("roleNames", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void createUser_passwordTooShort_fail() throws Exception {
        // WHEN: password chỉ 4 ký tự -> vi phạm @Size(min = 8, message =
        // "INVALID_PASSWORD") trong UserCreationRequest
        // THEN: trả về 400, không gọi tới userService.handleCreateUser()
        mockMvc.perform(multipart("/api/v1/admin/users")
                        .param("email", "john@example.com")
                        .param("password", "1234")
                        .param("roleNames", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }
}
