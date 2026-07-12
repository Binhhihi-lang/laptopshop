package com.example.laptopshop.controller.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.example.laptopshop.domain.User;
import com.example.laptopshop.service.UploadService;
import com.example.laptopshop.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class UserRestController {

    private final UserService userService;
    private final UploadService uploadService;
    private final PasswordEncoder passwordEncoder;

    public UserRestController(UserService userService, UploadService uploadService,
            PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.uploadService = uploadService;
        this.passwordEncoder = passwordEncoder;
    }

    // 1. Lấy danh sách toàn bộ người dùng
    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = this.userService.getAllUser();
        return ResponseEntity.ok(users);
    }

    // 2. Lấy thông tin chi tiết một người dùng
    @GetMapping("/{id}")
    public ResponseEntity<User> getUserById(@PathVariable long id) {
        User user = this.userService.getUserByID(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(user);
    }

    // 3. Tạo mới người dùng (Nhận dữ liệu dạng form-data để hỗ trợ upload ảnh đại
    // diện)
    @PostMapping
    public ResponseEntity<User> createUser(
            @Valid
            // Nhận dữ liệu người dùng từ form-data, ánh xạ vào đối tượng User
            @ModelAttribute User newUser,
            // Nhận file từ form-data với key là "inputFile", không bắt buộc phải có
            @RequestParam(value = "inputFile", required = false) MultipartFile file) {

        // Mã hóa mật khẩu
        String hashPassword = this.passwordEncoder.encode(newUser.getPassword());
        newUser.setPassword(hashPassword);

        // Upload avatar nếu có
        if (file != null && !file.isEmpty()) {
            String avatar = this.uploadService.handleSaveUploadFile(file, "avatar");
            newUser.setAvatar(avatar);
        }

        // Tìm role tương ứng và lưu
        if (newUser.getRole() != null && newUser.getRole().getName() != null) {
            newUser.setRole(this.userService.getRoleByName(newUser.getRole().getName()));
        }

        User savedUser = this.userService.handleSaveUser(newUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    // 4. Cập nhật thông tin người dùng
    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(
            @Valid @PathVariable long id,
            @ModelAttribute User userUpdate,
            @RequestParam(value = "inputFile", required = false) MultipartFile file) {

        User currentUser = this.userService.getUserByID(id);
        if (currentUser == null) {
            return ResponseEntity.notFound().build();
        }

        // Cập nhật thông tin cơ bản
        currentUser.setFullName(userUpdate.getFullName());
        currentUser.setPhone(userUpdate.getPhone());
        currentUser.setAddress(userUpdate.getAddress());

        // Upload avatar mới nếu có
        if (file != null && !file.isEmpty()) {
            // Xóa file cũ
            if (currentUser.getAvatar() != null) {
                this.uploadService.handleDeleteFile(currentUser.getAvatar(), "avatar");
            }
            String avatarUpdate = this.uploadService.handleSaveUploadFile(file, "avatar");
            currentUser.setAvatar(avatarUpdate);
        }

        // Cập nhật role
        if (userUpdate.getRole() != null && userUpdate.getRole().getName() != null) {
            currentUser.setRole(this.userService.getRoleByName(userUpdate.getRole().getName()));
        }

        User updatedUser = this.userService.handleSaveUser(currentUser);
        return ResponseEntity.ok(updatedUser);
    }

    // 5. Xóa người dùng
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable long id) {
        User user = this.userService.getUserByID(id);
        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        // Xóa avatar trước
        if (user.getAvatar() != null) {
            this.uploadService.handleDeleteFile(user.getAvatar(), "avatar");
        }

        this.userService.deleteUserById(id);
        return ResponseEntity.noContent().build();
    }
}
