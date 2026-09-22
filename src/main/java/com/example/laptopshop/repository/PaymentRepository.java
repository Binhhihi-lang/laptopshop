package com.example.laptopshop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Payment;
import com.example.laptopshop.domain.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    /** Tra lần thử theo mã giao dịch gửi sang cổng — dùng khi verify callback. */
    Optional<Payment> findByTxnRef(String txnRef);

    /** Toàn bộ lần thử của 1 đơn, mới nhất trước — cho trang chi tiết/đối soát. */
    List<Payment> findByOrderIdOrderByAttemptNoDesc(String orderId);

    /** Lần thử thành công của đơn (nếu có) — nguồn của Order.paymentStatus. */
    Optional<Payment> findFirstByOrderIdAndStatusOrderByAttemptNoDesc(String orderId, PaymentStatus status);

    /** Đếm số lần thử đã dùng — chặn khách spam nút thanh toán lại. */
    long countByOrderId(String orderId);
}
