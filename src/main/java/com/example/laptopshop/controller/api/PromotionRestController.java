package com.example.laptopshop.controller.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.Promotion.PromotionCreationRequest;
import com.example.laptopshop.dto.request.Promotion.PromotionUpdateRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.Promotion.PromotionResponse;
import com.example.laptopshop.service.PromotionService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
// Đủ tiền tố `v1` như mọi controller admin khác: SecurityConfiguration chỉ đặt
// hasAnyRole("ADMIN","STAFF") cho /api/v1/admin/**. Thiếu v1 thì URL rơi xuống
// anyRequest().authenticated() — CUSTOMER có token lọt qua được lớp chặn theo path.
@RequestMapping("/api/v1/admin/promotions")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PromotionRestController {

    PromotionService promotionService;

    @GetMapping
    @PreAuthorize("hasAuthority('READ_PROMOTION')")
    public ApiResponse<List<PromotionResponse>> getAll() {
        return ApiResponse.<List<PromotionResponse>>builder().result(promotionService.getAll()).build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_PROMOTION')")
    public ApiResponse<PromotionResponse> getById(@PathVariable String id) {
        return ApiResponse.<PromotionResponse>builder().result(promotionService.getById(id)).build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_PROMOTION')")
    public ApiResponse<PromotionResponse> create(@RequestBody @Valid PromotionCreationRequest request) {
        return ApiResponse.<PromotionResponse>builder().result(promotionService.create(request)).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE_PROMOTION')")
    public ApiResponse<PromotionResponse> update(@PathVariable String id,
            @RequestBody @Valid PromotionUpdateRequest request) {
        return ApiResponse.<PromotionResponse>builder().result(promotionService.update(id, request)).build();
    }

    /**
     * Ngừng áp dụng chương trình.
     *
     * <p>
     * Dùng DELETE trên URL cho quen thuộc với FE, nhưng thực chất là soft —
     * không có {@code DELETE_PROMOTION} trong hệ thống quyền vì chương trình đã
     * áp lên đơn phải giữ lại để tra cứu.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE_PROMOTION')")
    public ApiResponse<Void> deactivate(@PathVariable String id) {
        promotionService.deactivate(id);
        return ApiResponse.<Void>builder().build();
    }
}
