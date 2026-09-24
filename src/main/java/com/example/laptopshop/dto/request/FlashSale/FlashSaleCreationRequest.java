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
 * Ảnh banner đi kèm dạng form-data ({@code inputFile}) theo khuôn Coupon/Product;
 * {@code imageUrl} là đường dẫn online để admin dán ảnh có sẵn.
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

    /** Upload trực tiếp (form-data) — theo khuôn CouponCreationRequest. */
    private org.springframework.web.multipart.MultipartFile inputFile;

    /** Hoặc dán URL ảnh có sẵn. */
    private String imageUrl;

    /** Xóa banner đã có. */
    private boolean removeImage = false;

    @NotNull(message = "Phiên flash sale phải có sản phẩm")
    private List<FlashSaleItemRequest> items = new ArrayList<>();
}
