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

import com.example.laptopshop.domain.RefreshToken;
import com.example.laptopshop.domain.Role;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.response.User.UserResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.UserMapper;
import com.example.laptopshop.repository.CachedAuthoritiesRepository;
import com.example.laptopshop.repository.RefreshTokenRepository;
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
    @Mock
    private CachedAuthoritiesRepository cachedAuthoritiesRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    // --- 2. KHAI BÁO CLASS CẦN TEST ---
    // @InjectMocks tự động lấy các @Mock ở trên nhét vào constructor của UserService
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

    // =========================================================================
    // TEST METHOD: getActiveAuthorities() — PA1: thu hồi quyền khi USER bị khóa
    // =========================================================================

    @Test
    void getActiveAuthorities_userInactive_returnEmptyAuthorities() {
        // GIVEN: user CÒN trong DB nhưng đã bị KHÓA (active=false), sở hữu
        // 1 Role đang active đầy đủ quyền. Cache quyền chưa có (cache miss).
        dummyUser.setActive(false);
        Role activeRole = new Role();
        activeRole.setName("USER");
        activeRole.setActive(true);
        dummyUser.setRoles(new java.util.HashSet<>(java.util.List.of(activeRole)));

        when(cachedAuthoritiesRepository.findById(SAMPLE_ID)).thenReturn(java.util.Optional.empty());
        when(userRepository.findById(SAMPLE_ID)).thenReturn(java.util.Optional.of(dummyUser));

        // WHEN: request kế tiếp của user bị khóa đi qua CustomJwtAuthenticationConverter
        List<org.springframework.security.core.GrantedAuthority> authorities =
                userService.getActiveAuthorities(SAMPLE_ID);

        // THEN: quyền phải bị thu hồi TOÀN BỘ -> authorities RỖNG -> filter chain
        // sẽ trả 403 + code 6012 để FE logout. Đồng thời KHÔNG ghi kết quả rỗng
        // này vào cache Redis (nếu admin kích hoạt lại, quyền phải tính lại từ DB,
        // không bị kẹt cache rỗng tối đa 5 phút TTL).
        assertTrue(authorities.isEmpty(), "User bị khóa phải có authorities rỗng");
        verify(cachedAuthoritiesRepository, never()).save(any());
    }

    @Test
    void getActiveAuthorities_userActive_returnRoleAuthorities() {
        // GIVEN: user ACTIVE với 1 role đang active — hành vi cũ phải được giữ nguyên
        dummyUser.setActive(true);
        Role activeRole = new Role();
        activeRole.setName("USER");
        activeRole.setActive(true);
        dummyUser.setRoles(new java.util.HashSet<>(java.util.List.of(activeRole)));

        when(cachedAuthoritiesRepository.findById(SAMPLE_ID)).thenReturn(java.util.Optional.empty());
        when(userRepository.findById(SAMPLE_ID)).thenReturn(java.util.Optional.of(dummyUser));

        // WHEN
        List<org.springframework.security.core.GrantedAuthority> authorities =
                userService.getActiveAuthorities(SAMPLE_ID);

        // THEN: vẫn nhận ROLE_USER như trước, và kết quả được ghi cache 5 phút
        assertEquals(1, authorities.size());
        assertEquals("ROLE_USER", authorities.get(0).getAuthority());
        verify(cachedAuthoritiesRepository).save(any());
    }

    // =========================================================================
    // TEST METHOD: updateUsersActive() — PA3 + guard tự khóa / khóa ADMIN
    // =========================================================================

    @Test
    void updateUsersActive_lockSelf_throwException() {
        // GIVEN: admin gửi yêu cầu KHÓA có chứa chính id của mình
        List<String> ids = List.of(SAMPLE_ID);
        when(userRepository.findAllById(ids)).thenReturn(List.of(dummyUser));

        // WHEN & THEN: không cho tự khóa chính mình
        AppException exception = assertThrows(AppException.class,
                () -> userService.updateUsersActive(ids, false, SAMPLE_ID));
        assertEquals(ErrorCode.USER_CANNOT_DEACTIVATE_SELF, exception.getErrorCode());

        // Không được đụng tới DB khi request bất hợp lệ
        verify(userRepository, never()).saveAll(any());
    }

    @Test
    void updateUsersActive_activateSelf_isAllowed() {
        // GIVEN: admin KÍCH HOẠT lại tài khoản của chính mình — hợp lệ (không
        // phải hành vi tự hại), chỉ cấm hướng KHÓA
        List<String> ids = List.of(SAMPLE_ID);
        when(userRepository.findAllById(ids)).thenReturn(List.of(dummyUser));

        // WHEN & THEN: không ném lỗi
        assertDoesNotThrow(() -> userService.updateUsersActive(ids, true, SAMPLE_ID));
        verify(userRepository).saveAll(any());
    }

    @Test
    void updateUsersActive_lockAdminUser_throwException() {
        // GIVEN: user cần khóa thuộc role ADMIN — đồng bộ với guard của
        // RoleService.updateRolesActive (không cho vô hiệu hóa quản trị)
        Role adminRole = new Role();
        adminRole.setName("ADMIN");
        adminRole.setActive(true);
        dummyUser.setRoles(new java.util.HashSet<>(java.util.List.of(adminRole)));

        List<String> ids = List.of(SAMPLE_ID);
        when(userRepository.findAllById(ids)).thenReturn(List.of(dummyUser));

        // WHEN & THEN
        AppException exception = assertThrows(AppException.class,
                () -> userService.updateUsersActive(ids, false, "other-admin-id"));
        assertEquals(ErrorCode.USER_CANNOT_DEACTIVATE_ADMIN, exception.getErrorCode());
        verify(userRepository, never()).saveAll(any());
    }

    @Test
    void updateUsersActive_lockUsers_evictsCacheAndRevokesRefreshTokens() {
        // GIVEN: 2 user thường cần khóa, mỗi user có sẵn refresh token trong Redis
        User otherUser = new User();
        otherUser.setId("other-user-id");
        List<User> users = List.of(dummyUser, otherUser);
        List<String> ids = List.of(SAMPLE_ID, "other-user-id");

        when(userRepository.findAllById(ids)).thenReturn(users);
        when(refreshTokenRepository.findByUserId(SAMPLE_ID))
                .thenReturn(List.of(new RefreshToken()));
        when(refreshTokenRepository.findByUserId("other-user-id"))
                .thenReturn(List.of(new RefreshToken()));

        // WHEN: khóa hàng loạt
        userService.updateUsersActive(ids, false, "admin-doing-lock");

        // THEN:
        // 1. Cờ active=false được lưu xuống DB
        users.forEach(user -> assertFalse(user.isActive()));
        verify(userRepository).saveAll(users);

        // 2. Cache quyền của từng user bị xóa -> request kế tính lại từ DB
        //    và getActiveAuthorities sẽ trả rỗng (nhờ PA1) -> 6012 -> logout
        verify(cachedAuthoritiesRepository).deleteById(SAMPLE_ID);
        verify(cachedAuthoritiesRepository).deleteById("other-user-id");

        // 3. Refresh token của từng user bị thu hồi -> chặn tái cấp token
        //    ngay cả khi client chủ động gọi /auth/refresh (A-nhẹ)
        verify(refreshTokenRepository).findByUserId(SAMPLE_ID);
        verify(refreshTokenRepository).findByUserId("other-user-id");
        verify(refreshTokenRepository, times(2)).deleteAll(any());
    }

    @Test
    void updateUsersActive_activateUsers_doesNotRevokeRefreshTokens() {
        // GIVEN: kích hoạt lại (active=true) — không thu hồi refresh token,
        // chỉ xóa cache để quyền được tính lại từ DB (phòng cache rỗng cũ)
        List<String> ids = List.of(SAMPLE_ID);
        dummyUser.setActive(false);
        when(userRepository.findAllById(ids)).thenReturn(List.of(dummyUser));

        // WHEN
        userService.updateUsersActive(ids, true, "admin-doing-activate");

        // THEN: save + evict cache nhưng KHÔNG đụng vào kho refresh token
        verify(userRepository).saveAll(any());
        verify(cachedAuthoritiesRepository).deleteById(SAMPLE_ID);
        verify(refreshTokenRepository, never()).findByUserId(any());
        verify(refreshTokenRepository, never()).deleteAll(any());
    }
}