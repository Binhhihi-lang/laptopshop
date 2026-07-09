package com.example.laptopshop.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.laptopshop.domain.Category;
import com.example.laptopshop.repository.CategoryRepository;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<Category> getAllCategories() {
        return this.categoryRepository.findAll();
    }

    public Category getCategoryById(long id) {
        return this.categoryRepository.findById(id);
    }

    public Category handleSaveCategory(Category category) {
        return this.categoryRepository.save(category);
    }

    public void deleteCategoryById(long id) {
        this.categoryRepository.deleteById(id);
    }
}