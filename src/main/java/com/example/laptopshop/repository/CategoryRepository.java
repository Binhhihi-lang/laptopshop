package com.example.laptopshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, String> {
    Category save(Category category);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);

}