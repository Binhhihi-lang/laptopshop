package com.example.laptopshop.service;

import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.laptopshop.domain.Product;
import com.example.laptopshop.dto.response.DashboardStats;
import com.example.laptopshop.repository.CategoryRepository;
import com.example.laptopshop.repository.CouponRepository;
import com.example.laptopshop.repository.ProductRepository;
import com.example.laptopshop.repository.UserRepository;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class DashboardService {

    UserRepository userRepository;
    ProductRepository productRepository;
    CategoryRepository categoryRepository;
    CouponRepository couponRepository;

    // Tổng hợp số liệu cho Dashboard. Yêu cầu quyền READ_DASHBOARD (STAFF & ADMIN
    // đều có) — trả số đếm tổng hợp, KHÔNG trả danh sách user nên không lộ PII.
    @Transactional(readOnly = true)
    public DashboardStats getStats() {
        DashboardStats stats = new DashboardStats();
        stats.setUserCount(userRepository.count());
        stats.setActiveUserCount(userRepository.countByActiveTrue());
        stats.setProductCount(productRepository.count());
        stats.setCategoryCount(categoryRepository.count());
        stats.setCouponCount(couponRepository.count());

        List<Product> low = productRepository.findFirst5ByQuantityLessThanOrderByQuantityAsc(5);
        List<DashboardStats.LowStockProduct> lowDto = low.stream().map(p -> {
            DashboardStats.LowStockProduct dto = new DashboardStats.LowStockProduct();
            dto.setId(p.getId());
            dto.setCode(p.getCode());
            dto.setName(p.getName());
            dto.setQuantity(p.getQuantity());
            dto.setImage(p.getImage());
            return dto;
        }).toList();
        stats.setLowStockProducts(lowDto);
        stats.setLowStockCount((long) low.size());

        return stats;
    }
}
