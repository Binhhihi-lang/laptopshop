package com.example.laptopshop.controller.api.client;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.dto.response.ApiResponse;
import com.example.laptopshop.dto.response.FlashSale.FlashSaleResponse;
import com.example.laptopshop.service.FlashSaleService;

/**
 * Storefront: phiên flash sale công khai.
 * Đường dẫn: /api/v1/client/flash-sales — permitAll ở tầng filter.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RestController
@RequestMapping("/api/v1/client/flash-sales")
public class ClientFlashSaleController {

    FlashSaleService flashSaleService;

    /** Phiên đang chạy (null nếu không có). */
    @GetMapping("/active")
    public ApiResponse<FlashSaleResponse> getActive() {
        return ApiResponse.<FlashSaleResponse>builder()
                .result(flashSaleService.findActiveResponse(LocalDateTime.now())).build();
    }

    /** Phiên sắp tới — FE vẽ "Diễn ra lúc 19:00" (D31). */
    @GetMapping("/upcoming")
    public ApiResponse<List<FlashSaleResponse>> getUpcoming(@RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.<List<FlashSaleResponse>>builder()
                .result(flashSaleService.findUpcomingResponses(LocalDateTime.now(), limit)).build();
    }

    @GetMapping("/{id}")
    public ApiResponse<FlashSaleResponse> getById(@PathVariable String id) {
        return ApiResponse.<FlashSaleResponse>builder().result(flashSaleService.getResponseById(id)).build();
    }
}
