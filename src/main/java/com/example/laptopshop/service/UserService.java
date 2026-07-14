package com.example.laptopshop.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.Role;
import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.request.User.UserUpdateRequest;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.repository.RoleRepository;
import com.example.laptopshop.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final UploadService uploadService;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, RoleRepository roleRepository,
            UploadService uploadService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
        this.uploadService = uploadService;
    }

    public List<User> getAllUser() {
        return this.userRepository.findAll();
    }

    public List<User> getAllUserByEmail(String email) {
        return this.userRepository.findByEmail(email);
    }

    public User getUserByID(long id) {
        User user = this.userRepository.findById(id);
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    public void deleteUserById(long id) {
        User user = getUserByID(id); // kiểm tra tồn tại, nếu không
        this.userRepository.delete(user);
    }

    public Role getRoleByName(String name) {
        return this.roleRepository.findByName(name);
    }

    // Validate

    public void validateEmail(String email, Long currentId) {
        if (email == null || email.isBlank())
            throw new AppException(ErrorCode.USER_EMAIL_EMPTY);
        // Nếu mà trùng email mà khác Id thì ném lỗi
        String normalized = email.trim();
        boolean exists = currentId == null
                ? this.userRepository.existsByEmailIgnoreCase(normalized)
                : this.userRepository.existsByEmailIgnoreCaseAndIdNot(normalized, currentId);

        if (exists) {
            throw new AppException(ErrorCode.USER_EMAIL_ALREADY_EXISTS);
        }
    }

    public void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new AppException(ErrorCode.INVALID_PASSWORD);
        }
    }

    // nhận về DTO UserCreationRequest, validate dữ liệu, map sang Entity User, mã
    // hóa mật khẩu, lưu xuống DB
    public User handleCreateUser(UserCreationRequest request) {
        // 1. Validate dữ liệu thô từ DTO
        validateEmail(request.getEmail(), null);
        validatePassword(request.getPassword());

        // 2. Map dữ liệu từ DTO sang Entity User
        User newUser = new User();
        newUser.setEmail(request.getEmail().trim().toLowerCase());
        newUser.setFullName(request.getFullName());
        newUser.setPhone(request.getPhone());
        newUser.setAddress(request.getAddress());

        // 3. Mã hóa mật khẩu thô
        String hashPassword = this.passwordEncoder.encode(request.getPassword());
        newUser.setPassword(hashPassword);

        // 4. Xử lý lưu File avatar nếu có
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String avatarName = this.uploadService.handleSaveUploadFile(file, "avatar");
            newUser.setAvatar(avatarName);
        }

        // 5. Tìm và gán Role (Giả định logic gán role của bạn)
        // newUser.setRole(this.getRoleByName(request.getRoleName()));

        return this.userRepository.save(newUser);
    }

    public User handleUpdateUser(Long id, UserUpdateRequest request) {
        // 1. Tìm User cũ trong DB, không thấy thì ném lỗi
        User existingUser = getUserByID(id);

        // 2. Validate email mới xem có trùng với ai khác không
        validateEmail(request.getEmail(), id);

        // 3. Đổ dữ liệu mới từ DTO đè lên Entity cũ
        existingUser.setEmail(request.getEmail().trim());
        existingUser.setFullName(request.getFullName());
        existingUser.setPhone(request.getPhone());
        existingUser.setAddress(request.getAddress());

        // 4. Xử lý đổi ảnh avatar mới nếu có gửi lên file mới
        MultipartFile file = request.getInputFile();
        if (file != null && !file.isEmpty()) {
            String newAvatar = this.uploadService.handleSaveUploadFile(file, "avatar");
            existingUser.setAvatar(newAvatar);
        }

        // 5. Lưu Entity đã cập nhật dữ liệu mới xuống DB
        return this.userRepository.save(existingUser);
    }
}
