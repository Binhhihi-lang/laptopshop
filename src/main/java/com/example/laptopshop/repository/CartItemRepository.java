package com.example.laptopshop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, String> {

    Optional<CartItem> findByCartIdAndProductId(String cartId, String productId);
}
