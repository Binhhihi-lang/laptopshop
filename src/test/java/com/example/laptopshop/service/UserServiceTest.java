package com.example.laptopshop.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.laptopshop.domain.Role;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.response.User.UserResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.UserMapper;
import com.example.laptopshop.repository.RoleRepository;
import com.example.laptopshop.repository.UserRepository;

/**
 * @ExtendWith(MockitoExtension.class): Chỉ thị cho JUnit 5 bật tính năng Mockito.
 * KHÔNG dùng @SpringBootTest, bài test này hoàn toàn chạy bằng Java thuần, không
 * khởi động Spring Boot.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    // --- 1. KHAI BÁO CÁC DEPENDENCY GIẢ (MOCKS) ---
    // UserService thật đang cần 5 dependency này, ta dùng @Mock để tạo bản giả cho chúng.
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UploadService uploadService;
    @Mock
    private UserMapper userMapper;

    // --- 2. KHAI BÁO CLASS CẦN TEST ---
    // @InjectMocks tự động lấy 5 cái @Mock ở trên nhét vào constructor của UserService
    @InjectMocks
    private UserService userService;

    private User dummyUser;
    private final String SAMPLE_ID = "f47ac10b-58cc-4372-a567-0e02b2c3d479";
    private final String SAMPLE_EMAIL = "test@example.com";

    @BeforeEach
    void setUp() {
        // Chuẩn bị 1 object User mẫu dùng chung cho các bài test
        dummyUser = new User();
        dummyUser.setId(SAMPLE_ID);
        dummyUser.setEmail(SAMPLE_EMAIL);
    }

    // =========================================================================
    // TEST METHOD: getUserById()
    // =========================================================================

    @Test
    void getUserById_userExists_returnUser() {
        // GIVEN: Giả lập khi repository tìm kiếm ID, nó trả về dummyUser
        when(userRepository.findById(SAMPLE_ID)).thenReturn(Optional.of(dummyUser));

        // WHEN: Gọi method thật của UserService
        User result = userService.getUserById(SAMPLE_ID);

        // THEN: Đảm bảo kết quả trả ra đúng là user đó
        assertNotNull(result);
        assertEquals(SAMPLE_ID, result.getId());
    }

    @Test
    void getUserById_userNotFound_throwException() {
        // GIVEN: Giả lập DB không tìm thấy user (trả về Optional.empty)
        when(userRepository.findById(SAMPLE_ID)).thenReturn(Optional.empty());

        // WHEN & THEN: Dùng assertThrows để bắt lỗi AppException
        AppException exception = assertThrows(AppException.class,
                () -> userService.getUserById(SAMPLE_ID));

        // Kiểm tra xem lỗi ném ra có đúng là mã USER_NOT_FOUND không
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    // =========================================================================
    // TEST METHOD: validateEmail()
    // =========================================================================

    @Test
    void validateEmail_emailExists_throwException() {
        // GIVEN: Giả lập email đã tồn tại trong hệ thống (currentId = null khi tạo mới)
        when(userRepository.existsByEmailIgnoreCase(SAMPLE_EMAIL)).thenReturn(true);

        // WHEN & THEN: Gọi hàm validateEmail và bắt lỗi
        AppException exception = assertThrows(AppException.class,
                () -> userService.validateEmail(SAMPLE_EMAIL, null));

        // Đảm bảo lỗi ném ra là USER_EMAIL_ALREADY_EXISTS
        assertEquals(ErrorCode.USER_EMAIL_ALREADY_EXISTS, exception.getErrorCode());
    }

    // =========================================================================
    // TEST METHOD: handleCreateUser() (Logic phức tạp nhất)
    // =========================================================================

    @Test
    void handleCreateUser_validRequest_success() {
        // GIVEN: Chuẩn bị DTO đầu vào
        UserCreationRequest request = new UserCreationRequest();
        request.setEmail(SAMPLE_EMAIL);
        request.setPassword("12345678");
        request.setRoleNames(List.of("USER"));

        Role dummyRole = new Role();
        dummyRole.setName("USER");

        User mappedUser = new User(); // Đại diện cho object User sau khi Mapper chạy
        User savedUser = new User();  // Đại diện cho object User sau khi lưu vào DB
        savedUser.setId(SAMPLE_ID);
        UserResponse response = new UserResponse();
        response.setId(SAMPLE_ID);

        // --- Mocking (Lập trình kịch bản cho các dependency giả) ---
        // 1. Validate email: giả lập email chưa bị trùng
        when(userRepository.existsByEmailIgnoreCase(SAMPLE_EMAIL)).thenReturn(false);

        // 2. Mapper: giả lập việc map từ request ra Entity
        when(userMapper.toEntity(request)).thenReturn(mappedUser);

        // 3. PasswordEncoder: giả lập mã hóa mật khẩu
        when(passwordEncoder.encode("12345678")).thenReturn("hashed_password_123");

        // 4. Lấy Role: giả lập DB trả về Role "USER"
        when(roleRepository.findByName("USER")).thenReturn(Optional.of(dummyRole));

        // 5. Save: giả lập hàm save trả về savedUser
        when(userRepository.save(mappedUser)).thenReturn(savedUser);

        // 6. Mapper response: giả lập hàm map sang Response
        when(userMapper.toResponse(savedUser)).thenReturn(response);

        // WHEN: Thực thi hàm nghiệp vụ
        UserResponse actualResponse = userService.handleCreateUser(request);

        // THEN: 
        assertNotNull(actualResponse);
        assertEquals(SAMPLE_ID, actualResponse.getId());

        // Xác minh xem hàm mã hóa mật khẩu có thực sự được gọi không
        verify(passwordEncoder).encode("12345678");

        // Xác minh mật khẩu của mappedUser đã bị đổi thành mật khẩu mã hóa chưa (bước 3 trong code thật)
        assertEquals("hashed_password_123", mappedUser.getPassword());

        // Xác minh hàm save() của DB có thực sự được gọi đúng 1 lần với object mappedUser không
        verify(userRepository, times(1)).save(mappedUser);
    }
}