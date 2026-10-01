package com.example.laptopshop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.laptopshop.domain.Voucher;

public interface VoucherRepository extends JpaRepository<Voucher, String> {
    Voucher save(Voucher voucher);

    Optional<Voucher> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, String id);

    /**
     * Tăng {@code usedCount} theo kiểu atomic (BR-A03) — chống 2 khách dùng
     * voucher cuối cùng cùng lúc.
     *
     * <p>
     * Điều kiện {@code usedCount < usageLimit} nằm trong câu UPDATE nên DB tự
     * chặn; service kiểm tra số dòng bị ảnh hưởng, 0 dòng = vừa hết lượt.
     * {@code usageLimit} null hoặc 0 = không giới hạn (P3: voucher cũ).
     *
     * <p>
     * Trước đây chỗ gọi đọc {@code getUsedCount()} rồi cộng 1 — hai request song
     * song cùng đọc giá trị cũ, cùng ghi, nên voucher {@code usageLimit = 1} bị
     * dùng 2 lần. Đây là pattern giống {@code PromotionRepository}.
     *
     * <p>
     * Dùng {@code COALESCE} vì cột {@code used_count} thêm ở Sprint 1 nên voucher
     * cũ có thể là NULL; {@code NULL + 1 = NULL} sẽ làm điều kiện WHERE luôn sai
     * và voucher hợp lệ bị báo hết lượt oan.
     *
     * @return số dòng cập nhật được (0 = đã hết lượt)
     */
    @Modifying
    @Query("""
            UPDATE Voucher v
            SET v.usedCount = COALESCE(v.usedCount, 0) + 1
            WHERE v.id = :id
              AND (v.usageLimit IS NULL OR v.usageLimit <= 0
                   OR COALESCE(v.usedCount, 0) < v.usageLimit)
            """)
    int incrementUsedCount(@Param("id") String id);

    /**
     * Hoàn lượt khi hủy đơn (D12). Chặn dưới 0 để dữ liệu không âm nếu bị gọi lặp.
     */
    @Modifying
    @Query("""
            UPDATE Voucher v
            SET v.usedCount = CASE
                    WHEN COALESCE(v.usedCount, 0) > 0 THEN v.usedCount - 1
                    ELSE 0
                END
            WHERE v.id = :id
            """)
    int decrementUsedCount(@Param("id") String id);
}
