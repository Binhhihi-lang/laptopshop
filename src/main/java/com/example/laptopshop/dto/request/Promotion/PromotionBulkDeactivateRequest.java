package com.example.laptopshop.dto.request.Promotion;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

import lombok.Getter;

/** Ngừng áp nhiều chương trình khuyến mại cùng lúc (bulk toolbar). */
@Getter
public class PromotionBulkDeactivateRequest {

    @NotEmpty(message = "INVALID_PROMOTION_CONFIG")
    private List<String> ids;

}
