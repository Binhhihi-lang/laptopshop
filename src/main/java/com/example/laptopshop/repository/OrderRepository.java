package com.example.laptopshop.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentStatus;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByOrderCode(String orderCode);

    // Lịch sử đơn của 1 khách — mới nhất trước.
    Page<Order> findByUserIdOrderByOrderDateDesc(String userId, Pageable pageable);

    // Tra đơn theo id VÀ chủ sở hữu: chặn khách xem đơn của người khác bằng
    // cách đoán id.
    Optional<Order> findByIdAndUserId(String id, String userId);

    /**
     * Danh sách đơn cho admin — lọc theo trạng thái / thanh toán / khoảng ngày
     * / từ khóa, tất cả optional (null = bỏ qua điều kiện).
     *
     * Từ khóa tìm trên mã đơn, tên người nhận và SĐT người nhận.
     * EntityGraph nạp sẵn orderDetails + user để tránh N+1 khi map response.
     */
    @EntityGraph(attributePaths = { "orderDetails", "user" })
    @Query("""
            SELECT o FROM Order o
            WHERE (:status IS NULL OR o.status = :status)
              AND (:paymentStatus IS NULL OR o.paymentStatus = :paymentStatus)
              AND (:fromDate IS NULL OR o.orderDate >= :fromDate)
              AND (:toDate IS NULL OR o.orderDate <= :toDate)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(o.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(o.receiverFullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR o.receiverPhone LIKE CONCAT('%', :keyword, '%'))
            """)
    Page<Order> searchAdmin(
            @Param("status") OrderStatus status,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("keyword") String keyword,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable);

    /** Nạp đơn kèm chi tiết + khách hàng cho trang chi tiết của admin. */
    @EntityGraph(attributePaths = { "orderDetails", "user", "coupon" })
    Optional<Order> findWithDetailsById(String id);

    /** Đếm số đơn theo từng trạng thái — dùng cho thẻ thống kê ở đầu trang. */
    long countByStatus(OrderStatus status);

    /** Tổng tiền của các đơn ở một trạng thái (dùng tính doanh thu đã hoàn thành). */
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = :status")
    long sumTotalPriceByStatus(@Param("status") OrderStatus status);
}
