package com.example.laptopshop.service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Role;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.request.User.UserUpdateRequest;
import com.example.laptopshop.dto.response.User.UserResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.mapper.UserMapper;
import com.example.laptopshop.repository.RoleRepository;
import com.example.laptopshop.repository.UserRepository;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)

@Service
public class UserService {

     UserRepository userRepository;
     PasswordEncoder passwordEncoder;
     RoleRepository roleRepository;
     UploadService uploadService;
     UserMapper userMapper;

    public User getUserById(String id) {
        return this.userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    // Dùng cho AuthenticationService lúc đăng nhập -> so khớp password, KHÔNG
    // qua @Where filter là chưa xóa mềm vì đã tự động áp dụng ở tầng Entity,
    public User getUserByEmail(String email) {
        return this.userRepository.findByEmail(email);
    }

    // Xóa MỀM: gọi y hệt như xóa thật trước đây, nhưng nhờ @SQLDelete khai báo
    // ở User.java, Hibernate tự động đổi câu lệnh thành UPDATE deleted_at =
    // NOW() thay vì DELETE thật
    public void deleteUserById(String id) {
        User user = getUserById(id); // kiểm tra tồn tại, nếu không
        if (user.getAvatar() != null) {
            this.uploadService.handleDeleteFile(user.getAvatar());
        }
        this.userRepository.delete(user);
    }

    public Role getRoleByName(String name) {
        return this.roleRepository.findByName(name)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
    }

    // nhiều Role cùng lúc theo danh sách tên, dùng cho create/update User
    // có nhiều Role. Nếu 1 tên bất kỳ không tồn tại -> ném ROLE_NOT_FOUND
    public Set<Role> getRolesByNames(List<String> names) {
        Set<Role> roles = new HashSet<>();
        for (String name : names) {
            roles.add(getRoleByName(name));
        }
        return roles;
    }

    // Validate
    public void validateEmail(String email, String currentId) {

        // Nếu mà trùng email mà khác Id thì ném lỗi. Nhờ @Where ở User.java,
        // existsByEmailIgnoreCase(...) TỰ ĐỘNG chỉ tính user CHƯA xóa mềm ->
        // email của 1 user đã xóa mềm được coi là "còn trống", có thể đăng ký
        // lại bình thường.
        String normalized = email.trim();
        boolean exists = currentId == null
                ? this.userRepository.existsByEmailIgnoreCase(normalized)
                : this.userRepository.existsByEmailIgnoreCaseAndIdNot(normalized, currentId);

        if (exists) {
            throw new AppException(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
        }
    }

    // ---- Các method trả Response DTO: LUÔN @Transactional để Hibernate Session
    // còn mở trong lúc MapStruct đọc user.getRoles() (quan hệ @ManyToMany, lazy),
    // tránh LazyInitializationException ----

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUserResponses() {
        List<User> users = this.userRepository.findAll();
        return this.userMapper.toResponseList(users);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserResponseById(String id) {
        User user = getUserById(id);
        return this.userMapper.toResponse(user);
    }

    // nhận về DTO UserCreationRequest, validate dữ liệu, map sang Entity User, mã
    // hóa mật khẩu, lưu xuống DB rồi map luôn sang Response TRONG CÙNG
    // transaction trước khi trả về Controller.
    @Transactional
    public UserResponse handleCreateUser(UserCreationRequest request) {
        // 1. Validate dữ liệu thô từ DTO
        validateEmail(request.getEmail(), null);

        // 2. Map các field thuần (fullName, phone, address) từ DTO sang Entity qua
        // MapStruct. password/avatar/roles/email KHÔNG được map ở đây (đã ignore
        // trong UserMapper) vì cần xử lý riêng bên dưới.
        User newUser = this.userMapper.toEntity(request);
        newUser.setEmail(request.getEmail().trim());

        // 3. Mã hóa mật khẩu thô
        String hashPassword = this.passwordEncoder.encode(request.getPassword());
        newUser.setPassword(hashPassword);

        // 4. Xử lý lưu File avatar nếu có
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String avatarName = this.uploadService.handleSaveUploadFile(file, "avatar");
            newUser.setAvatar(avatarName);
        }

        // set Role
        Set<Role> roles = getRolesByNames(request.getRoleNames());
        newUser.setRoles(roles);

        User saved = this.userRepository.save(newUser);
        return this.userMapper.toResponse(saved);
    }

    @Transactional
    public UserResponse handleUpdateUser(String id, UserUpdateRequest request) {
        // 1. Tìm User cũ trong DB, không thấy thì ném lỗi
        User existingUser = getUserById(id);

        // 2. Validate email mới xem có trùng với ai khác không
        validateEmail(request.getEmail(), id);
        Set<Role> roles = getRolesByNames(request.getRoleNames());

        // 3. Đổ các field thuần (fullName, phone, address) từ DTO đè lên Entity cũ
        // qua MapStruct (@MappingTarget), rồi set riêng email/roles bên dưới
        this.userMapper.updateEntity(request, existingUser);
        existingUser.setEmail(request.getEmail().trim());
        existingUser.setRoles(roles);

        // 4. Xử lý đổi ảnh avatar mới nếu có gửi lên file mới
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String newAvatar = this.uploadService.handleSaveUploadFile(file, "avatar");
            existingUser.setAvatar(newAvatar);
        }

        // 5. Lưu Entity đã cập nhật dữ liệu mới xuống DB
        User saved = this.userRepository.save(existingUser);
        return this.userMapper.toResponse(saved);
    }

    // Cập nhật lastLoginAt khi user đăng nhập thành công
    @Transactional
    public void updateLastLoginAt(String userId, LocalDateTime lastLoginAt) {
        User user = getUserById(userId);
        user.setLastLoginAt(lastLoginAt);
        this.userRepository.save(user);
    }
}
