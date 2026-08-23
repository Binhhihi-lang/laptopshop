package com.example.laptopshop.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Role;

public interface RoleRepository extends JpaRepository<Role, String> {

    // tìm kiếm role theo tên để hiện thị ds role đổ vào combobox khi tạo/sửa user
    Optional<Role> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);

    // Tìm các Role chứa ít nhất 1 permission trong danh sách id — dùng để thu
    // hồi cache quyền của user khi permission bị khóa/xóa (quan hệ nhiều-nhiều
    // role_permissions, truy vấn theo cột permission_id).
    List<Role> findDistinctByPermissions_IdIn(List<String> permissionIds);
}
