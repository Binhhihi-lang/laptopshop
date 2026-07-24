package com.example.laptopshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;
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
}
