package com.example.laptopshop.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.laptopshop.domain.FlashSale;

public interface FlashSaleRepository extends JpaRepository<FlashSale, String> {

    /** Phiên ĐANG diễn ra tại {@code now}, kết thúc sớm nhất trước (D31). */
    @Query("""
            SELECT f FROM FlashSale f
            WHERE f.active = true
              AND f.startAt <= :now
              AND f.endAt >= :now
            ORDER BY f.endAt ASC
            """)
    List<FlashSale> findActiveBetween(@Param("now") LocalDateTime now);

    /** Phiên SẮP tới — FE vẽ "Diễn ra lúc 19:00" (D31). */
    @Query("""
            SELECT f FROM FlashSale f
            WHERE f.active = true
              AND f.startAt > :now
            ORDER BY f.startAt ASC
            """)
    List<FlashSale> findUpcoming(@Param("now") LocalDateTime now);

    /** Danh sách quản trị — xếp theo giờ mở phiên để bảng đọc như lịch trong ngày. */
    List<FlashSale> findAllByOrderByStartAtAsc();

    /**
     * Tìm phiên CHƯA kết thúc có item trùng sản phẩm VÀ khung giờ giao nhau với
     * {@code [startAt, endAt]} — dùng để chặn tạo/sửa phiên trùng (TASK-001).
     *
     * <p>
     * Bỏ qua {@code excludeId} (chính phiên đang sửa). Chỉ xét phiên còn hiệu lực
     * ({@code endAt >= now}) — phiên đã hết/dừng không cản admin tạo phiên mới.
     * Giao nhau: {@code startAt <= newEnd AND endAt >= newStart}.
     */
    @Query("""
            SELECT DISTINCT f FROM FlashSale f
            JOIN f.items i
            WHERE f.id <> :excludeId
              AND f.endAt >= :now
              AND f.startAt <= :endAt
              AND f.endAt >= :startAt
              AND i.product.id IN :productIds
            """)
    List<FlashSale> findOverlapping(@Param("excludeId") String excludeId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("now") LocalDateTime now,
            @Param("productIds") List<String> productIds);
}
