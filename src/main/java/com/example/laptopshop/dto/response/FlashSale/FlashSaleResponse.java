package com.example.laptopshop.dto.response.FlashSale;

import java.time.LocalDateTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Phiên flash sale trả về cho admin và cho trang /flash-sale của khách. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlashSaleResponse {

    private String id;
    private String name;
    private String description;
    private String bannerImage;
    private LocalDateTime startAt;
    private LocalDateTime endAt;

    /** Công tắc admin bật/tắt. */
    private boolean active;

    /**
     * Đang chạy THẬT tại thời điểm gọi (active + trong khung giờ). Tách khỏi
     * {@code active} để FE khỏi tự so sánh đồng hồ máy khách với múi giờ server —
     * nguồn chân lý là BE.
     */
    private boolean running;

    private Integer itemCount;
    private List<FlashSaleItemResponse> items;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
