package com.example.laptopshop.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.laptopshop.domain.FlashSaleItem;

public interface FlashSaleItemRepository extends JpaRepository<FlashSaleItem, String> {

    /**
     * Giá flash hiện hành của một nhóm sản phẩm — nguồn duy nhất của
     * {@code resolvePriceMap} (§3.1b). Một câu theo {@code productIds} để tránh
     * N+1 (R19).
     *
     * <p>
     * Về soft delete: Hibernate KHÔNG áp {@code @SQLRestriction} của bảng cha lên
     * join to-one. Phiên đã xóa mềm luôn đi kèm {@code active=false} (service set
     * cả hai khi delete) nên vẫn bị loại; sản phẩm xóa mềm thì không bao giờ tới
     * được {@code productIds} ở đây. Điều kiện giờ nằm trong câu để DB quyết.
     */
    @Query("""
            SELECT i FROM FlashSaleItem i
            JOIN FETCH i.product p
            JOIN FETCH i.flashSale s
            WHERE p.id IN :productIds
              AND s.active = true
              AND s.startAt <= :now
              AND s.endAt >= :now
            ORDER BY s.endAt ASC
            """)
    List<FlashSaleItem> findCurrentByProductIds(@Param("productIds") List<String> productIds,
            @Param("now") LocalDateTime now);

    /** Toàn bộ item của một phiên, kèm product — cho response admin. */
    @Query("""
            SELECT i FROM FlashSaleItem i
            JOIN FETCH i.product p
            WHERE i.flashSale.id = :flashSaleId
            ORDER BY i.flashPrice ASC
            """)
    List<FlashSaleItem> findByFlashSaleId(@Param("flashSaleId") String flashSaleId);

    /**
     * Trừ kho phiên atomic (D29). Điều kiện {@code soldInFlash + qty <= flashStock}
     * nằm trong UPDATE nên DB tự chặn; 2 khách chốt máy cuối song song thì đúng
     * 1 câu có tác dụng. 0 dòng = kho cạn.
     */
    @Modifying
    @Query("""
            UPDATE FlashSaleItem i
            SET i.soldInFlash = i.soldInFlash + :qty
            WHERE i.id = :id
              AND i.soldInFlash + :qty <= i.flashStock
            """)
    int consumeStock(@Param("id") String id, @Param("qty") long qty);

    /**
     * Hoàn kho phiên khi hủy đơn (nối D12). Chặn dưới 0 để dữ liệu không âm nếu
     * bị gọi lặp.
     */
    @Modifying
    @Query("""
            UPDATE FlashSaleItem i
            SET i.soldInFlash = CASE
                    WHEN i.soldInFlash >= :qty THEN i.soldInFlash - :qty
                    ELSE 0
                END
            WHERE i.id = :id
              AND i.soldInFlash > 0
            """)
    int releaseStock(@Param("id") String id, @Param("qty") long qty);

    /**
     * Đếm máy khách này đã mua trong một phiên (D32). Đi qua OrderDetail vì đơn
     * không lưu id item flash; phiên suy ra từ product + khung giờ. Đơn canceled
     * bị loại để hủy không mất lượt.
     */
    @Query("""
            SELECT COALESCE(SUM(d.quantity), 0) FROM OrderDetail d
            WHERE d.order.user.id = :userId
              AND d.product.id = :productId
              AND d.order.status <> com.example.laptopshop.domain.OrderStatus.CANCELLED
              AND d.order.orderDate >= :startAt
              AND d.order.orderDate <= :endAt
            """)
    long countQtyBoughtByUserInWindow(@Param("userId") String userId,
            @Param("productId") String productId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt);
}
