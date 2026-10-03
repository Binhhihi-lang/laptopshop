package com.example.laptopshop.dto.request.FlashSale;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO tạo/cập nhật một phiên flash sale, gồm cả danh sách sản phẩm trong phiên.
 *
 * <p>
 * Gửi dạng form-data: toàn bộ DTO đóng gói thành 1 part JSON {@code flashSaleInfo}
 * (khuôn Product). Phiên không có ảnh.
 */
@Getter
@Setter
public class FlashSaleCreationRequest {

    @NotBlank(message = "Tên phiên không được để trống")
    private String name;

    private String description;

    @NotNull(message = "Thời gian bắt đầu không được để trống")
    private LocalDateTime startAt;

    @NotNull(message = "Thời gian kết thúc không được để trống")
    private LocalDateTime endAt;

    /** null = bật (một phiên tạo ra là để chạy). */
    private Boolean active;

    @NotNull(message = "Phiên flash sale phải có sản phẩm")
    private List<FlashSaleItemRequest> items = new ArrayList<>();
}
