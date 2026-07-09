package com.example.laptopshop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Coupon;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Coupon save(Coupon coupon);

    void deleteById(long id);

    Coupon findById(long id);

    Optional<Coupon> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}