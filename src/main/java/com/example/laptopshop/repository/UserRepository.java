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

    // tìm kiếm người dùng theo id
    User findById(long id);

}
