package com.example.laptopshop.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Order;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByOrderCode(String orderCode);

    // Lịch sử đơn của 1 khách — mới nhất trước.
    Page<Order> findByUserIdOrderByOrderDateDesc(String userId, Pageable pageable);

    // Tra đơn theo id VÀ chủ sở hữu: chặn khách xem đơn của người khác bằng
    // cách đoán id.
    Optional<Order> findByIdAndUserId(String id, String userId);
}
