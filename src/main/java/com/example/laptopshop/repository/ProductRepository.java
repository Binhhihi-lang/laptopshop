package com.example.laptopshop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.laptopshop.domain.Product;

public interface ProductRepository extends JpaRepository<Product, String> {
    Product save(Product product);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, String id);

    // Tìm product theo CODE (SKU, ví dụ "LP-G100") — dùng cho URL thân thiện SEO
    // của storefront (/products/{code}). KHÁC findById (tra theo UUID khoá chính);
    // trước đây storefront gọi nhầm findById(code) nên luôn 404.
    Optional<Product> findByCodeIgnoreCase(String code);

    // Đếm số sản phẩm theo từng category (1 query group-by) để hiển thị cột
    // "Số sản phẩm" ở trang danh sách category. Trả về [categoryId, count].
    @Query("SELECT p.category.id, COUNT(p) FROM Product p WHERE p.category IS NOT NULL GROUP BY p.category.id")
    List<Object[]> countProductsByCategory();

    // Lấy tối đa 5 sản phẩm sắp hết hàng (quantity < qty), sắp xếp tăng dần theo
    // số lượng. Tự động áp dụng @SQLRestriction (soft-delete: deleted_at IS NULL).
    List<Product> findFirst5ByQuantityLessThanOrderByQuantityAsc(int qty);

    // Tìm kiếm sản phẩm cho nhánh client (storefront): chỉ lấy product đang
    // active VÀ thuộc category đang active (quy tắc storefront đã chốt).
    // Tất cả filter là optional — NULL nghĩa là không lọc theo tiêu chí đó.
    // keyword tìm trong name + shortDesc (case-insensitive, contains).
    // Page<Product> để Spring tự gắn totalElements/totalPages cho phân trang.
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND p.category IS NOT NULL
              AND p.category.active = true
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:factory IS NULL OR LOWER(p.factory) = LOWER(:factory))
              AND (:minPrice IS NULL OR p.price >= :minPrice)
              AND (:maxPrice IS NULL OR p.price <= :maxPrice)
              AND (:keyword IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.shortDesc) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Product> searchStorefront(
            @Param("categoryId") String categoryId,
            @Param("factory") String factory,
            @Param("minPrice") Long minPrice,
            @Param("maxPrice") Long maxPrice,
            @Param("keyword") String keyword,
            Pageable pageable);

    // Lấy tối đa 8 sản phẩm liên quan (cùng category, đang active, trừ chính
    // sản phẩm đang xem). Dùng cho mục "Sản phẩm liên quan" ở trang chi tiết.
    // Tự động áp dụng @SQLRestriction (soft-delete: deleted_at IS NULL).
    @Query("""
            SELECT p FROM Product p
            WHERE p.active = true
              AND p.category IS NOT NULL
              AND p.category.active = true
              AND p.category.id = :categoryId
              AND p.id <> :excludeId
            ORDER BY p.sold DESC
            """)
    List<Product> findRelatedByCategory(@Param("categoryId") String categoryId,
            @Param("excludeId") String excludeId,

            Pageable pageable);

    // Lấy danh sách hãng (factory) duy nhất của các sản phẩm đang active.
    // Dùng cho filter "Hãng" ở trang danh sách sản phẩm.
    @Query("SELECT DISTINCT p.factory FROM Product p WHERE p.active = true AND p.factory IS NOT NULL ORDER BY p.factory ASC")
    List<String> findDistinctActiveFactories();

    // Tìm kiếm cho picker ở trang quản trị (chọn sản phẩm cho phạm vi khuyến mại,
    // banner...). Khác searchStorefront: KHÔNG lọc theo category active — admin
    // cần thấy cả sản phẩm thuộc danh mục đang tắt để gán khuyến mại.
    // keyword tìm trong name + code (case-insensitive, contains).
    @Query("""
            SELECT p FROM Product p
            WHERE (:keyword IS NULL
                   OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(p.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY p.name ASC
            """)
    Page<Product> searchForPicker(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Trừ tồn kho + tăng lượt bán theo kiểu atomic (BR-A04) — chống 2 đơn song
     * song bán vượt số hàng còn lại.
     *
     * <p>
     * Điều kiện {@code quantity >= :qty} nằm trong câu UPDATE nên DB tự chặn;
     * 0 dòng = vừa bị khách khác mua hết.
     *
     * <p>
     * Phải set {@code updatedAt = CURRENT_TIMESTAMP} ngay trong câu lệnh:
     * {@code updatedAt} là {@code @LastModifiedDate} do Hibernate ghi lúc
     * {@code save()}, nên UPDATE kiểu này KHÔNG đi qua Hibernate → cột "Ngày sửa"
     * ở màn quản lý sản phẩm sẽ đứng yên nếu quên.
     *
     * @return số dòng cập nhật được (0 = không đủ hàng)
     */
    @Modifying
    @Query("""
            UPDATE Product p
            SET p.quantity = p.quantity - :qty,
                p.sold = p.sold + :qty,
                p.updatedAt = CURRENT_TIMESTAMP
            WHERE p.id = :id
              AND p.quantity >= :qty
            """)
    int deductStock(@Param("id") String id, @Param("qty") long qty);

    /**
     * Hoàn tồn kho + giảm lượt bán khi hủy đơn. Chặn {@code sold} dưới 0 để dữ
     * liệu không âm nếu bị gọi lặp.
     */
    @Modifying
    @Query("""
            UPDATE Product p
            SET p.quantity = p.quantity + :qty,
                p.sold = CASE WHEN p.sold >= :qty THEN p.sold - :qty ELSE 0 END,
                p.updatedAt = CURRENT_TIMESTAMP
            WHERE p.id = :id
            """)
    int restoreStock(@Param("id") String id, @Param("qty") long qty);
}
