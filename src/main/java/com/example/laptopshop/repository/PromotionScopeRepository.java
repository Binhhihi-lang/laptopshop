package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.laptopshop.domain.PromotionScope;

@Repository
public interface PromotionScopeRepository extends JpaRepository<PromotionScope, String> {

    List<PromotionScope> findByPromotionId(String promotionId);

    void deleteByPromotionId(String promotionId);
}
