package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import com.example.laptopshop.domain.User;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.request.User.UserUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.service.UploadService;
import com.example.laptopshop.service.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
public class UserRestController {

    private final UserService userService;
    private final UploadService uploadService;

    public UserRestController(UserService userService, UploadService uploadService) {
        this.userService = userService;
        this.uploadService = uploadService;
    }

    // 1. Lấy danh sách toàn bộ người dùng
    @GetMapping
    public ApiResponse<List<User>> getAllUsers() {
        List<User> users = this.userService.getAllUser();
        ApiResponse<List<User>> response = new ApiResponse<>();
        response.setResult(users);
        return response;
    }

    // 2. Lấy thông tin chi tiết một người dùng
    @GetMapping("/{id}")
    public ApiResponse<User> getUserById(@PathVariable long id) {
        User user = this.userService.getUserByID(id);
        ApiResponse<User> response = new ApiResponse<>();
        response.setResult(user);
        return response;
    }

    // 3. Tạo mới người dùng (Nhận dữ liệu dạng form-data để hỗ trợ upload ảnh đại
    // diện)
    @PostMapping
    public ApiResponse<User> createUser(@Valid @ModelAttribute UserCreationRequest request) {

        User savedUser = this.userService.handleCreateUser(request);
        ApiResponse<User> response = new ApiResponse<>();
        response.setResult(savedUser);
        return response;
    }

    // 4. Cập nhật thông tin người dùng
    @PutMapping("/{id}")
    public ApiResponse<User> updateUser(
            @PathVariable Long id,
            @Valid @ModelAttribute UserUpdateRequest request) {
        User updatedUser = this.userService.handleUpdateUser(id, request);
        ApiResponse<User> response = new ApiResponse<>();
        response.setResult(updatedUser);
        return response;
    }

    // Xóa người dùng
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable long id) {
        User user = this.userService.getUserByID(id);

        // Xóa avatar trước
        if (user.getAvatar() != null) {
            this.uploadService.handleDeleteFile(user.getAvatar());
        }

        this.userService.deleteUserById(id);
        ApiResponse<Void> response = new ApiResponse<>();
        return response;
    }
}
