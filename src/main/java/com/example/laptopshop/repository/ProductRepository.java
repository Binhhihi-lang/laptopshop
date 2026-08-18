package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.example.laptopshop.domain.Product;

public interface ProductRepository extends JpaRepository<Product, String> {
    Product save(Product product);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, String id);

    // Đếm số sản phẩm theo từng category (1 query group-by) để hiển thị cột
    // "Số sản phẩm" ở trang danh sách category. Trả về [categoryId, count].
    @Query("SELECT p.category.id, COUNT(p) FROM Product p WHERE p.category IS NOT NULL GROUP BY p.category.id")
    List<Object[]> countProductsByCategory();
}
