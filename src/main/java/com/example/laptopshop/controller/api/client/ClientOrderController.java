package com.example.laptopshop.controller.api.client;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Client.CreateOrderRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Client.OrderDetailResponse;
import com.example.laptopshop.dto.response.Client.OrderSummaryResponse;
import com.example.laptopshop.exception.AppException;
import com.example.laptopshop.exception.ErrorCode;
import com.example.laptopshop.service.OrderService;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Storefront: đơn hàng của khách đã đăng nhập.
 * Đường dẫn: /api/v1/client/orders
 *
 * Mọi truy vấn đều lọc theo userId lấy từ JWT → khách không xem/hủy được đơn
 * của người khác kể cả khi đoán được id.
 */
@RestController
@RequestMapping("/api/v1/client/orders")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ClientOrderController {

    OrderService orderService;

    // Tạo đơn từ giỏ server hiện tại (body KHÔNG chứa danh sách sản phẩm).
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<OrderDetailResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        ApiResponse<OrderDetailResponse> response = new ApiResponse<>();
        response.setResult(this.orderService.createOrder(currentUserId(jwt), request));
        return response;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Page<OrderSummaryResponse>> getMyOrders(
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        ApiResponse<Page<OrderSummaryResponse>> response = new ApiResponse<>();
        response.setResult(this.orderService.getMyOrders(currentUserId(jwt), pageable));
        return response;
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<OrderDetailResponse> getMyOrderDetail(
            @PathVariable String orderId,
            @AuthenticationPrincipal Jwt jwt) {
        ApiResponse<OrderDetailResponse> response = new ApiResponse<>();
        response.setResult(this.orderService.getMyOrderDetail(currentUserId(jwt), orderId));
        return response;
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<OrderDetailResponse> cancelMyOrder(
            @PathVariable String orderId,
            @AuthenticationPrincipal Jwt jwt) {
        ApiResponse<OrderDetailResponse> response = new ApiResponse<>();
        response.setResult(this.orderService.cancelMyOrder(currentUserId(jwt), orderId));
        return response;
    }

    private String currentUserId(Jwt jwt) {
        String userId = jwt == null ? null : jwt.getClaimAsString("userId");
        if (userId == null || userId.isBlank()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        return userId;
    }
}
