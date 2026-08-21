package com.example.laptopshop.controller.api;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import com.example.laptopshop.dto.request.User.UserBulkDeleteRequest;
import com.example.laptopshop.dto.request.User.UserBulkStatusRequest;
import com.example.laptopshop.dto.request.User.UserCreationRequest;
import com.example.laptopshop.dto.request.User.UserProfileUpdateRequest;
import com.example.laptopshop.dto.request.User.UserUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.User.UserResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.service.UserService;

import jakarta.validation.Valid;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserRestController {

    UserService userService;

    // 1. Lấy danh sách toàn bộ người dùng (đã xóa mềm sẽ tự động không xuất hiện
    // nhờ @Where khai báo ở User.java)
    @GetMapping
    @PreAuthorize("hasAuthority('READ_USER')")
    public ApiResponse<List<UserResponse>> getAllUsers() {
        ApiResponse<List<UserResponse>> response = new ApiResponse<>();
        response.setResult(this.userService.getAllUserResponses());
        return response;
    }

    // 2. Lấy thông tin chi tiết một người dùng
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_USER')")
    public ApiResponse<UserResponse> getUserById(@PathVariable String id) {
        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(this.userService.getUserResponseById(id));
        return response;
    }

    // 3. Tạo mới người dùng (Nhận dữ liệu dạng form-data để hỗ trợ upload ảnh đại
    // diện; roleNames là danh sách vì 1 user giờ có thể có nhiều Role)
    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_USER')")
    public ApiResponse<UserResponse> createUser(@Valid @ModelAttribute UserCreationRequest request) {
        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(this.userService.handleCreateUser(request));
        return response;
    }

    // 4. Cập nhật thông tin người dùng
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE_USER')")
    public ApiResponse<UserResponse> updateUser(
            @PathVariable String id,
            @Valid @ModelAttribute UserUpdateRequest request) {

        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(this.userService.handleUpdateUser(id, request));
        return response;
    }

    // 5. Xóa MỀM người dùng. Về code Controller/Service KHÔNG đổi gì so với xóa
    // thật trước đây — cơ chế soft-delete nằm hoàn toàn ở annotation
    // @SQLDelete/@Where khai báo tại User.java. Vẫn xóa avatar vật lý trên đĩa
    // như cũ vì file ảnh không có khái niệm "xóa mềm".
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_USER')")
    public ApiResponse<Void> deleteUser(@PathVariable String id) {
        this.userService.deleteUserById(id);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Người dùng đã được xóa thành công");
        return response;
    }

    // 6. Xóa hàng loạt người dùng theo danh sách id (body JSON { ids: [...] }).
    // Việc xóa avatar từng user + xóa record nằm trong 1 transaction ở
    // UserService.deleteUsersByIds() để đảm bảo nhất quán.
    @PostMapping("/bulk-delete")
    @PreAuthorize("hasAuthority('DELETE_USER')")
    public ApiResponse<Void> deleteUsers(
            @Valid @RequestBody UserBulkDeleteRequest request) {
        this.userService.deleteUsersByIds(request.getIds());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Các người dùng đã được xóa thành công");
        return response;
    }

    // 7. Kích hoạt/khóa hàng loạt người dùng (body JSON { ids: [...], active:
    // true/false })
    @PatchMapping("/bulk-status")
    @PreAuthorize("hasAuthority('UPDATE_USER')")
    public ApiResponse<Void> updateUsersActive(
            @Valid @RequestBody UserBulkStatusRequest request) {
        this.userService.updateUsersActive(request.getIds(), request.isActive());
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage(request.isActive()
                ? "Các người dùng đã được kích hoạt thành công"
                : "Các người dùng đã được khóa thành công");
        return response;
    }

    // 8. Lấy hồ sơ cá nhân của chính người dùng đang đăng nhập.
    // Chỉ cần đã xác thực (isAuthenticated) — KHÔNG yêu cầu READ_USER, để STAFF
    // hay bất kỳ tài khoản nào cũng xem được thông tin của chính mình.
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> getMyProfile() {
        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(this.userService.getMyProfile(getCurrentUserId()));
        return response;
    }

    // 9. Cập nhật hồ sơ cá nhân của chính người dùng đang đăng nhập.
    // Chỉ nhận fullName/phone/address/avatar (xem UserProfileUpdateRequest),
    // đảm bảo không thể tự đổi email / vai trò / quyền.
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserResponse> updateMyProfile(
            @ModelAttribute UserProfileUpdateRequest request) {
        ApiResponse<UserResponse> response = new ApiResponse<>();
        response.setResult(this.userService.handleUpdateMyProfile(getCurrentUserId(), request));
        response.setMessage("Cập nhật hồ sơ cá nhân thành công");
        return response;
    }

    // Lấy userId của người dùng hiện tại từ claim "userId" của JWT trong
    // SecurityContext. Converter CustomJwtAuthenticationConverter đã đưa claim
    // này vào token, nên luôn có khi request đã qua oauth2ResourceServer.
    private String getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getClaimAsString("userId");
        }
        throw new AppException(ErrorCode.UNAUTHENTICATED);
    }
}
