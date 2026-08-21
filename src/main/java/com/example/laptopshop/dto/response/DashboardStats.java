package com.example.laptopshop.dto.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DashboardStats {

    private Long userCount;
    private Long activeUserCount;
    private Long productCount;
    private Long categoryCount;
    private Long couponCount;
    private Long lowStockCount;
    private List<LowStockProduct> lowStockProducts;

    @Getter
    @Setter
    public static class LowStockProduct {
        private String id;
        private String code;
        private String name;
        private Long quantity;
        private String image;
    }
}
