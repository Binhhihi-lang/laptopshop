package com.example.laptopshop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Cart;

public interface CartRepository extends JpaRepository<Cart, String> {

    Optional<Cart> findByUserId(String userId);
}
