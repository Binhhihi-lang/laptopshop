package com.example.laptopshop.dto.response.HomeBanner;

import java.time.LocalDateTime;

import com.example.laptopshop.domain.BannerTargetType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Một slide carousel trang chủ.
 *
 * <p>
 * {@code targetType}/{@code targetValue} được trả nguyên vẹn để FE tự quyết
 * định {@code routerLink} — response không chứa URL đã dựng sẵn, vì route của
 * Angular là việc của FE (xem §0.6).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeBannerResponse {

    private String id;
    private String title;
    private String subtitle;
    private String image;
    private String bgColor;
    private BannerTargetType targetType;
    private String targetValue;
    private Integer sortOrder;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
