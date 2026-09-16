package com.example.laptopshop.controller.api;

import java.util.List;

import com.example.laptopshop.dto.request.Auth.LogoutRequest;
import com.example.laptopshop.dto.request.Auth.RefreshTokenRequest;
import com.example.laptopshop.dto.request.Auth.RevokeDeviceLoginRequest;
import com.example.laptopshop.dto.response.DeviceInfoResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.utils.DeviceRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Auth.AuthenticationRequest;
import com.example.laptopshop.dto.request.Auth.IntrospectRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.AuthenticationResponse;
import com.example.laptopshop.dto.response.IntrospectResponse;
import com.example.laptopshop.service.AuthenticationService;

import jakarta.validation.Valid;

@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AuthenticationController {

   AuthenticationService authenticationService;

    // Đăng nhập bằng email + password, trả về JWT nếu đúng.
    // ADMIN/STAFF KHÔNG bị giới hạn số thiết bị (xem AuthenticationService.authenticate),
    // nhưng vẫn ghi phiên để hiện ở trang quản lý thiết bị.
    @PostMapping("/login")
    public ApiResponse<AuthenticationResponse> login(@Valid @RequestBody AuthenticationRequest request,
            HttpServletRequest httpRequest) {
        AuthenticationResponse result = this.authenticationService.authenticate(request,
                DeviceRequestUtils.getDeviceId(httpRequest),
                DeviceRequestUtils.getUserAgent(httpRequest),
                DeviceRequestUtils.getClientIp(httpRequest));
        ApiResponse<AuthenticationResponse> response = new ApiResponse<>();
        response.setResult(result);
        return response;
    }

    // Kiểm tra 1 token còn hợp lệ không (chữ ký đúng + chưa hết hạn)
    @PostMapping("/introspect")
    public ApiResponse<IntrospectResponse> introspect(@RequestBody IntrospectRequest request) {
        IntrospectResponse result = this.authenticationService.introspect(request);
        ApiResponse<IntrospectResponse> response = new ApiResponse<>();
        response.setResult(result);
        return response;
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        this.authenticationService.logout(request);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Logout thành công");
        return response;
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthenticationResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthenticationResponse result = this.authenticationService.refreshToken(request);
        ApiResponse<AuthenticationResponse> response = new ApiResponse<>();
        response.setResult(result);
        return response;
    }

    // ================== QUẢN LÝ THIẾT BỊ ĐĂNG NHẬP ==================

    // Liệt kê thiết bị đang đăng nhập. Chỉ cần xác thực, không cần quyền đặc biệt
    // -> mọi ADMIN/STAFF đều xem được thiết bị của chính mình.
    @GetMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<List<DeviceInfoResponse>> listDevices(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        ApiResponse<List<DeviceInfoResponse>> response = new ApiResponse<>();
        response.setResult(this.authenticationService.listDevices(
                requireUserId(jwt), DeviceRequestUtils.getDeviceId(httpRequest)));
        return response;
    }

    // Đăng xuất 1 thiết bị cụ thể.
    @DeleteMapping("/devices/{deviceId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> revokeDevice(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String deviceId) {
        this.authenticationService.revokeDevice(requireUserId(jwt), deviceId);
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Đã đăng xuất thiết bị");
        return response;
    }

    // Đăng xuất mọi thiết bị KHÁC (giữ thiết bị đang dùng).
    @PostMapping("/devices/revoke-others")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> revokeOtherDevices(@AuthenticationPrincipal Jwt jwt,
            HttpServletRequest httpRequest) {
        this.authenticationService.revokeOtherDevices(
                requireUserId(jwt), DeviceRequestUtils.getDeviceId(httpRequest));
        ApiResponse<Void> response = new ApiResponse<>();
        response.setMessage("Đã đăng xuất các thiết bị khác");
        return response;
    }

    // Đăng xuất THIẾT BỊ ĐÃ CHỌN khi login bị chặn vì vượt giới hạn (dự phòng:
    // admin hiện được miễn, nhưng giữ endpoint cho nhất quán với client và phòng
    // khi chính sách thay đổi).
    @PostMapping("/devices/revoke-and-login")
    public ApiResponse<AuthenticationResponse> revokeDeviceAndLogin(
            @Valid @RequestBody RevokeDeviceLoginRequest request) {
        ApiResponse<AuthenticationResponse> response = new ApiResponse<>();
        response.setResult(this.authenticationService.revokeDeviceAndLogin(
                request.getRevokeTicket(), request.getTargetDeviceId()));
        return response;
    }

    /** Claim "userId" do AuthenticationService cấp; thiếu -> coi như chưa xác thực. */
    private String requireUserId(Jwt jwt) {
        String userId = jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
