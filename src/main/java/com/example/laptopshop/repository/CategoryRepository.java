package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, String> {
    Category save(Category category);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);

    // Lấy danh sách Category đang active, sắp xếp theo thứ tự hiển thị.
    // Dùng cho nhánh client (storefront) — chỉ hiển thị category đang bật.
    List<Category> findByActiveTrueOrderByDisplayOrderAsc();

}