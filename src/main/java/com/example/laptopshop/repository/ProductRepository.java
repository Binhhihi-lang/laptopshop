package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.laptopshop.domain.Product;

public interface ProductRepository extends JpaRepository<Product, String> {
    Product save(Product product);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, String id);

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
}
