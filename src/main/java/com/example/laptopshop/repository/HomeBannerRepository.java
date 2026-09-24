package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.HomeBanner;

public interface HomeBannerRepository extends JpaRepository<HomeBanner, String> {

    /** Slide khách thấy ở trang chủ: chỉ banner đang bật, theo thứ tự admin xếp. */
    List<HomeBanner> findByActiveTrueOrderBySortOrderAsc();

    /** Danh sách cho trang quản trị — gồm cả banner đang tắt. */
    List<HomeBanner> findAllByOrderBySortOrderAsc();
}
