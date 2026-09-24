package com.example.laptopshop.controller.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.request.FlashSale.FlashSaleCreationRequest;
import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.FlashSale.FlashSaleResponse;
import com.example.laptopshop.service.FlashSaleService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/** Admin: quản lý phiên flash sale. Ảnh banner qua @ModelAttribute (khuôn Coupon). */
@RestController
@RequestMapping("/api/v1/admin/flash-sales")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class FlashSaleRestController {

    FlashSaleService flashSaleService;

    @GetMapping
    @PreAuthorize("hasAuthority('READ_FLASH_SALE')")
    public ApiResponse<List<FlashSaleResponse>> getAll() {
        return ApiResponse.<List<FlashSaleResponse>>builder().result(flashSaleService.getAllResponses()).build();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('READ_FLASH_SALE')")
    public ApiResponse<FlashSaleResponse> getById(@PathVariable String id) {
        return ApiResponse.<FlashSaleResponse>builder().result(flashSaleService.getResponseById(id)).build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE_FLASH_SALE')")
    public ApiResponse<FlashSaleResponse> create(@Valid @ModelAttribute FlashSaleCreationRequest request) {
        return ApiResponse.<FlashSaleResponse>builder()
                .result(flashSaleService.create(request, request.getInputFile())).build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('UPDATE_FLASH_SALE')")
    public ApiResponse<FlashSaleResponse> update(@PathVariable String id,
            @Valid @ModelAttribute FlashSaleCreationRequest request) {
        return ApiResponse.<FlashSaleResponse>builder()
                .result(flashSaleService.update(id, request, request.getInputFile())).build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE_FLASH_SALE')")
    public ApiResponse<Void> delete(@PathVariable String id) {
        flashSaleService.deleteFlashSale(id);
        return ApiResponse.<Void>builder().build();
    }
}
