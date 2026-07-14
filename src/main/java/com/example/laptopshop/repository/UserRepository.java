package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.laptopshop.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User save(User user);

    void deleteById(long id);

    // tìm kiếm người dùng theo email
    List<User> findByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    // Dùng cho Chỉnh sửa: Kiểm tra email có trùng với ai khác hay không
    // Nếu email trùng với chính nó thì không sao, nhưng nếu trùng với người khác
    // thì báo lỗi
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    // tìm kiếm người dùng theo id
    User findById(long id);

}
