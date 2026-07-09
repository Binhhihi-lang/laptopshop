package com.example.laptopshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Category save(Category category);

    void deleteById(long id);

    // tìm kiếm theo id
    Category findById(long id);
}