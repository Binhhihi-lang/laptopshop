package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.laptopshop.domain.Promotion;

public interface PromotionRepository extends JpaRepository<Promotion, String> {

    /**
     * Promotion đang chạy để engine xét giảm giá.
     *
     * <p>
     * Lọc ngay ở DB theo {@code active} + khung thời gian; các điều kiện phụ
     * thuộc từng dòng hàng (scope, loại trừ, minQuantity, minOrderValue) do
     * engine xử lý vì chúng phụ thuộc sản phẩm trong giỏ.
     *
     * <p>
     * KHÔNG dùng {@code JOIN FETCH} cho scopes/excludes: Hibernate ném
     * {@code MultipleBagFetchException} khi fetch 2 collection {@code List} cùng
     * lúc ("cannot simultaneously fetch multiple bags") — lỗi này làm chết cả
     * luồng đặt hàng khi có bất kỳ chương trình nào đang chạy. Engine chỉ đọc
     * scope/exclude của vài chương trình đang chạy nên lazy-load là đủ.
     */
    @Query("""
            SELECT p FROM Promotion p
            WHERE p.active = true
              AND p.startDate <= :now
              AND p.endDate >= :now
            """)
    List<Promotion> findActiveAt(@Param("now") java.time.LocalDateTime now);

    /**
     * Tăng {@code usedCount} theo kiểu atomic (D19) — chống 2 request song song
     * cùng vượt ngân sách.
     *
     * <p>
     * Điều kiện {@code usedCount < usageLimit} nằm trong câu UPDATE nên DB tự
     * chặn; service kiểm tra số dòng bị ảnh hưởng, 0 dòng = hết lượt.
     * {@code usageLimit} null = không giới hạn.
     *
     * @return số dòng cập nhật được (0 = đã hết lượt)
     */
    @Modifying
    @Query("""
            UPDATE Promotion p
            SET p.usedCount = p.usedCount + 1
            WHERE p.id = :id
              AND (p.usageLimit IS NULL OR p.usedCount < p.usageLimit)
            """)
    int incrementUsedCount(@Param("id") String id);

    /**
     * Hoàn lượt khi hủy đơn (D12). Chặn dưới 0 để dữ liệu không âm nếu vì lý do
     * nào đó bị gọi 2 lần.
     */
    @Modifying
    @Query("""
            UPDATE Promotion p
            SET p.usedCount = p.usedCount - 1
            WHERE p.id = :id
              AND p.usedCount > 0
            """)
    int decrementUsedCount(@Param("id") String id);
}
