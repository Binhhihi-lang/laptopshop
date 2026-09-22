package com.example.laptopshop.domain;

import java.time.LocalDateTime;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Một LẦN THỬ thanh toán của đơn — quan hệ 1 đơn : N lần thử.
 *
 * Tách khỏi Order vì khách được phép thanh toán lại khi lần trước thất bại:
 * mỗi lần thử cần một vnp_TxnRef riêng (VNPay không cho trùng mã giao dịch) và
 * phải giữ lại lịch sử để đối soát / hoàn tiền. Order.paymentStatus chỉ là
 * trạng thái TỔNG HỢP lấy từ lần thử thành công.
 */
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** Mã giao dịch gửi sang cổng thanh toán (vnp_TxnRef) — duy nhất toàn hệ thống. */
    @Column(nullable = false, unique = true, length = 100)
    private String txnRef;

    /** Lần thử thứ mấy của đơn, bắt đầu từ 1. */
    @Column(nullable = false)
    private int attemptNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    /**
     * Trạng thái của RIÊNG lần thử này: PENDING (vừa tạo, chờ khách trả),
     * PAID (thành công), FAILED (khách hủy / cổng báo lỗi / hết hạn).
     * REFUNDED là khái niệm của cả đơn nên không dùng ở đây.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** Số tiền của lần thử — chụp lại tại thời điểm tạo để đối chiếu với cổng. */
    private Long amount;

    // ===== Dữ liệu cổng thanh toán trả về (null khi chưa có callback) =====
    private String responseCode; // vnp_ResponseCode
    private String transactionNo; // vnp_TransactionNo — mã giao dịch phía VNPay
    private String bankCode; // vnp_BankCode
    private String payDate; // vnp_PayDate
    private String transactionStatus; // vnp_TransactionStatus

    /** Toàn bộ tham số callback dạng JSON — giữ thô để tra cứu khi lệch đối soát. */
    @Column(columnDefinition = "TEXT")
    private String rawResponse;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
