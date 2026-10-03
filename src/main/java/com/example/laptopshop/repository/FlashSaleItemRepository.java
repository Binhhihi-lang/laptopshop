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
     * Trừ kho phiên atomic (D29). {@code flashStock} là số CÒN LẠI nên trừ thẳng;
     * điều kiện {@code flashStock >= qty} nằm trong UPDATE nên DB tự chặn. 0 dòng =
     * kho cạn.
     */
    @Modifying
    @Query("""
            UPDATE FlashSaleItem i
            SET i.flashStock = i.flashStock - :qty,
                i.soldInFlash = i.soldInFlash + :qty
            WHERE i.id = :id
              AND i.flashStock >= :qty
            """)
    int consumeStock(@Param("id") String id, @Param("qty") long qty);

    /**
     * Hoàn kho phiên khi hủy đơn (nối D12): cộng lại {@code flashStock}, trừ
     * {@code soldInFlash}. Chặn dưới 0 để dữ liệu không âm nếu bị gọi lặp.
     */
    @Modifying
    @Query("""
            UPDATE FlashSaleItem i
            SET i.flashStock = i.flashStock + :qty,
                i.soldInFlash = CASE
                    WHEN i.soldInFlash >= :qty THEN i.soldInFlash - :qty
                    ELSE 0
                END
            WHERE i.id = :id
            """)
    int releaseStock(@Param("id") String id, @Param("qty") long qty);

    /**
     * Đếm máy khách này đã mua cho từng item flash (BR-F14/D32), gộp theo
     * {@code flashSaleItemId} mà đơn đã lưu lúc chốt.
     *
     * <p>
     * Khớp ĐÚNG item (không dùng khung giờ phiên) nên hai phiên trùng giờ không
     * đếm lẫn nhau. Đơn {@code CANCELLED} bị loại để hủy không mất lượt.
     *
     * <p>
     * Trả về {@code [flashSaleItemId, tổngQty]} cho từng item khách đã mua; item
     * chưa mua lần nào KHÔNG có dòng — chỗ gọi tự hiểu là 0.
     */
    @Query("""
            SELECT d.flashSaleItemId, COALESCE(SUM(d.quantity), 0) FROM OrderDetail d
            WHERE d.order.user.id = :userId
              AND d.flashSaleItemId IN :itemIds
              AND d.order.status <> com.example.laptopshop.domain.OrderStatus.CANCELLED
            GROUP BY d.flashSaleItemId
            """)
    List<Object[]> sumQtyBoughtByUserForItems(@Param("userId") String userId,
            @Param("itemIds") List<String> itemIds);
}
