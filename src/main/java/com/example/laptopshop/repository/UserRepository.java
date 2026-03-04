package com.example.laptopshop.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.laptopshop.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    User save(User chubinh);

    void deleteById(long id);

    // lắp ghép hàm để thêm tính năng
    List<User> findByEmail(String email);

    User findById(long id);

}
