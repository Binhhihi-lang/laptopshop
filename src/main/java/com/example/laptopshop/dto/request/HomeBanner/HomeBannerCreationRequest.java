package com.example.laptopshop.dto.request.HomeBanner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import com.example.laptopshop.domain.BannerTargetType;

/**
 * DTO tạo/cập nhật một slide carousel trang chủ.
 *
 * <p>
 * Link không phải một URL tuyệt đối admin tự dán, mà là cặp
 * {@code targetType} + {@code targetValue} để FE tự build {@code routerLink}
 * (§0.6, D30). Nhờ vậy chặn được {@code javascript:} và link ra ngoài.
 */
@Getter
@Setter
public class HomeBannerCreationRequest {

    @NotBlank(message = "Tiêu đề banner không được để trống")
    private String title;

    private String subtitle;

    /** Upload trực tiếp (form-data) — theo khuôn CouponCreationRequest. */
    private org.springframework.web.multipart.MultipartFile inputFile;

    /** Hoặc dán URL ảnh có sẵn. */
    private String imageUrl;

    /** Xóa ảnh hiện tại khi không gửi file mới. */
    private boolean removeImage = false;

    /** Màu nền chữ/viền, vd "#0F172A". null = FE dùng nền mặc định. */
    private String bgColor;

    @NotNull(message = "Loại liên kết không được để trống")
    private BannerTargetType targetType;

    /**
     * Định danh theo {@code targetType}: Product.id | Category.id | tên hãng |
     * FlashSale.id | đường dẫn "/..." (chỉ khi targetType = URL).
     */
    @NotBlank(message = "Nơi dẫn tới không được để trống")
    private String targetValue;

    /** Thứ tự hiển thị, nhỏ trước. null = 0. */
    private Integer sortOrder;

    /** null = bật. */
    private Boolean active;
}
