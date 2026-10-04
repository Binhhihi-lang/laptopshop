package com.example.laptopshop.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.laptopshop.domain.Order;
import com.example.laptopshop.domain.OrderStatus;
import com.example.laptopshop.domain.PaymentMethod;
import com.example.laptopshop.domain.PaymentStatus;

public interface OrderRepository extends JpaRepository<Order, String> {

    Optional<Order> findByOrderCode(String orderCode);

    // Lịch sử đơn của 1 khách — mới nhất trước.
    Page<Order> findByUserIdOrderByOrderDateDesc(String userId, Pageable pageable);

    // Tra đơn theo id VÀ chủ sở hữu: chặn khách xem đơn của người khác bằng
    // cách đoán id.
    Optional<Order> findByIdAndUserId(String id, String userId);

    // Tra đơn theo mã đơn + chủ sở hữu — dùng khi khách mở cổng thanh toán VNPay.
    Optional<Order> findByOrderCodeAndUserId(String orderCode, String userId);

    /**
     * Danh sách đơn cho admin — lọc theo trạng thái / thanh toán / khoảng ngày
     * / từ khóa, tất cả optional (null = bỏ qua điều kiện).
     *
     * Từ khóa tìm trên mã đơn, tên người nhận và SĐT người nhận.
     * EntityGraph nạp sẵn orderDetails + user khi lấy order để tránh N+1 khi map response.
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
    @EntityGraph(attributePaths = { "orderDetails", "user", "voucher" })
    Optional<Order> findWithDetailsById(String id);

    /** Đếm số đơn theo từng trạng thái — dùng cho thẻ thống kê ở đầu trang. */
    long countByStatus(OrderStatus status);

    /**
     * Đơn VNPay chưa trả tiền đã quá hạn giữ hàng — job dọn đơn dùng để hủy và
     * hoàn tồn kho. Lọc theo orderDate nên không cần cột hạn riêng.
     */
    @EntityGraph(attributePaths = { "orderDetails" })
    List<Order> findByPaymentMethodAndPaymentStatusInAndStatusInAndOrderDateBefore(
            PaymentMethod paymentMethod,
            Collection<PaymentStatus> paymentStatuses,
            Collection<OrderStatus> statuses,
            LocalDateTime cutoff);

    /** Tổng tiền của các đơn ở một trạng thái (dùng tính doanh thu đã hoàn thành). */
    @Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = :status")
    long sumTotalPriceByStatus(@Param("status") OrderStatus status);

    // ===== DASHBOARD (BR-D01..BR-D05) =====

    /** Đếm đơn theo TỪNG trạng thái trong 1 query — thay 5 lần countByStatus. */
    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countGroupByStatus();

    /**
     * Doanh thu (tổng {@code totalPrice}) của đơn ở một trạng thái trong khoảng
     * {@code [from, to)}. Dùng {@code [from, to)} nửa mở để không đếm trùng đơn
     * nằm đúng ranh giới giữa kỳ này và kỳ liền trước.
     */
    @Query("""
            SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o
            WHERE o.status = :status
              AND o.orderDate >= :from
              AND o.orderDate < :to
            """)
    long sumRevenueInRange(
            @Param("status") OrderStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Doanh thu + số đơn theo NGÀY trong khoảng {@code [from, to)} — dữ liệu vẽ
     * biểu đồ. Trả về {@code [java.sql.Date, Long revenue, Long orderCount]},
     * chỉ gồm ngày CÓ đơn; ngày trống do service tự bù 0.
     */
    @Query("""
            SELECT CAST(o.orderDate AS date), COALESCE(SUM(o.totalPrice), 0), COUNT(o)
            FROM Order o
            WHERE o.status = :status
              AND o.orderDate >= :from
              AND o.orderDate < :to
            GROUP BY CAST(o.orderDate AS date)
            ORDER BY CAST(o.orderDate AS date) ASC
            """)
    List<Object[]> sumRevenueByDay(
            @Param("status") OrderStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Đếm đơn đặt trong khoảng {@code [from, to)}. */
    long countByOrderDateGreaterThanEqualAndOrderDateLessThan(LocalDateTime from, LocalDateTime to);

    /**
     * Top sản phẩm bán chạy theo SỐ LƯỢNG trong kỳ (BR-D05) — chỉ đơn
     * {@code COMPLETED}.
     *
     * <p>
     * Nhóm theo các cột SNAPSHOT trên {@code OrderDetail} (code/name/image) chứ
     * không join {@code Product}: dòng chi tiết giữ nguyên thông tin tại thời
     * điểm mua, nên sản phẩm bị xoá mềm sau đó vẫn thống kê đúng.
     *
     * <p>
     * {@code LEFT JOIN} lấy thêm {@code Product.id} để FE có link; sản phẩm đã
     * xoá mềm sẽ cho id null (dòng vẫn giữ, không bị mất khỏi bảng xếp hạng).
     *
     * <p>
     * Trả về {@code [productId, code, name, image, quantitySold, revenue]}.
     */
    @Query("""
            SELECT p.id, od.productCode, od.productName, od.productImage,
                   SUM(od.quantity), SUM(od.quantity * od.price)
            FROM Order o
            JOIN o.orderDetails od
            LEFT JOIN od.product p
            WHERE o.status = :status
              AND o.orderDate >= :from
              AND o.orderDate < :to
            GROUP BY p.id, od.productCode, od.productName, od.productImage
            ORDER BY SUM(od.quantity) DESC
            """)
    List<Object[]> findTopSellingProducts(
            @Param("status") OrderStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    // ===== BÁO CÁO KHUYẾN MẠI (BR-BL14) =====
    // Loại đơn CANCELLED khỏi mọi truy vấn dưới đây: đơn huỷ không phải chi phí
    // khuyến mại thật (BR-BL14a).

    /** Tổng chi phí khuyến mại (promotion + voucher) của đơn KHÔNG huỷ trong kỳ. */
    @Query("""
            SELECT COALESCE(SUM(o.promotionDiscount), 0), COALESCE(SUM(o.voucherDiscount), 0)
            FROM Order o
            WHERE o.status <> :cancelled
              AND o.orderDate >= :from
              AND o.orderDate < :to
            """)
    List<Object[]> sumDiscountCost(
            @Param("cancelled") OrderStatus cancelled,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Tổng số đơn + số đơn CÓ dùng khuyến mại (promotion hoặc voucher) trong kỳ. */
    @Query("""
            SELECT COUNT(o),
                   COALESCE(SUM(CASE WHEN COALESCE(o.promotionDiscount, 0) > 0
                                       OR COALESCE(o.voucherDiscount, 0) > 0
                                     THEN 1 ELSE 0 END), 0)
            FROM Order o
            WHERE o.status <> :cancelled
              AND o.orderDate >= :from
              AND o.orderDate < :to
            """)
    List<Object[]> countOrdersAndDiscounted(
            @Param("cancelled") OrderStatus cancelled,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Hiệu quả từng CHƯƠNG TRÌNH KHUYẾN MẠI (promotion): số đơn đã áp + tiền đã giảm.
     * Gom theo {@code OrderDetail.promotionId} trên đơn KHÔNG huỷ.
     * Trả về {@code [promotionId, orderCount, discountAmount]}.
     */
    @Query("""
            SELECT od.promotionId, COUNT(DISTINCT o.id), COALESCE(SUM(od.discountAmount), 0)
            FROM Order o
            JOIN o.orderDetails od
            WHERE o.status <> :cancelled
              AND o.orderDate >= :from
              AND o.orderDate < :to
              AND od.promotionId IS NOT NULL
            GROUP BY od.promotionId
            """)
    List<Object[]> sumPromotionEffect(
            @Param("cancelled") OrderStatus cancelled,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /** Hiệu quả từng VOUCHER: số đơn đã dùng + tiền đã giảm. */
    @Query("""
            SELECT o.voucher.id, o.voucher.code, COUNT(o), COALESCE(SUM(o.voucherDiscount), 0)
            FROM Order o
            WHERE o.status <> :cancelled
              AND o.orderDate >= :from
              AND o.orderDate < :to
              AND o.voucher IS NOT NULL
            GROUP BY o.voucher.id, o.voucher.code
            """)
    List<Object[]> sumVoucherEffect(
            @Param("cancelled") OrderStatus cancelled,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    /**
     * Hiệu quả FLASH SALE: số dòng đã bán + tiền giảm (giá gốc − giá flash).
     *
     * <p>
     * {@code originalPrice} chỉ có từ V15 nên đơn cũ = NULL → dùng
     * {@code COALESCE(od.originalPrice, od.price)} để không tính ra số âm/null.
     */
    @Query("""
            SELECT COUNT(DISTINCT o.id),
                   COALESCE(SUM(od.quantity), 0),
                   COALESCE(SUM((COALESCE(od.originalPrice, od.price) - od.price) * od.quantity), 0)
            FROM Order o
            JOIN o.orderDetails od
            WHERE o.status <> :cancelled
              AND o.orderDate >= :from
              AND o.orderDate < :to
              AND od.flashSaleItemId IS NOT NULL
            """)
    List<Object[]> sumFlashSaleEffect(
            @Param("cancelled") OrderStatus cancelled,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);
}
