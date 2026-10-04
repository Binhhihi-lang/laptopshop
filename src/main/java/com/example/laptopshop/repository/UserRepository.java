package com.example.laptopshop.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.example.laptopshop.domain.User;

public interface UserRepository extends JpaRepository<User, String> {
    User save(User user);

    // tìm kiếm người dùng theo email
    User findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    // Dùng cho Chỉnh sửa: Kiểm tra email có trùng với ai khác hay không
    // Nếu email trùng với chính nó thì không sao, nhưng nếu trùng với người khác
    // thì báo lỗi
    boolean existsByEmailIgnoreCaseAndIdNot(String email, String id);

    boolean existsByEmail(String email);

    // Đếm số user đang active (dùng cho KPI "Người dùng hoạt động" trên Dashboard)
    long countByActiveTrue();

    /** Đếm khách đăng ký trong khoảng thời gian (Dashboard — BR-D06, cần READ_USER). */
    long countByCreatedAtAfter(java.time.LocalDateTime since);

    /**
     * Tìm khách cho picker gán voucher. Lọc theo tên hoặc email, chỉ tài khoản
     * đang hoạt động — gán cho tài khoản đã khoá thì khách không dùng được.
     */
    @Query("""
            SELECT u FROM User u
            WHERE u.active = true
              AND (:keyword IS NULL
                   OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY u.fullName ASC
            """)
    Page<User> searchForPicker(@Param("keyword") String keyword, Pageable pageable);
}
