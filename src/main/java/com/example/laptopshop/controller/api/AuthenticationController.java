package com.example.laptopshop.controller.api;

import com.example.laptopshop.dto.request.Auth.LogoutRequest;
import com.example.laptopshop.dto.request.Auth.RefreshTokenRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
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

    // Đăng nhập bằng email + password, trả về JWT nếu đúng
    @PostMapping("/login")
    public ApiResponse<AuthenticationResponse> login(@Valid @RequestBody AuthenticationRequest request) {
        AuthenticationResponse result = this.authenticationService.authenticate(request);
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
}
