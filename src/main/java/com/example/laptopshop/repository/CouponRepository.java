package com.example.laptopshop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, String> {
    Coupon save(Coupon coupon);

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, String id);
}