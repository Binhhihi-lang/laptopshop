package com.example.laptopshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.example.laptopshop.domain.Product;

public interface ProductRepository extends JpaRepository<Product, String> {
    Product save(Product product);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, String id);
}
