package com.example.laptopshop.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

    // tìm kiếm role theo tên để hiện thị ds role đổ vào combobox khi tạo/sửa user
    Role findByName(String name);
}
