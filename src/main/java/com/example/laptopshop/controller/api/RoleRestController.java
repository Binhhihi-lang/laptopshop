package com.example.laptopshop.controller.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.laptopshop.domain.Role;
import com.example.laptopshop.repository.RoleRepository;

// Controller nhỏ, chỉ phục vụ lấy danh sách Role để đổ vào dropdown ở giao diện
// (form tạo/sửa User cần chọn ROLE_ADMIN / ROLE_USER)
@RestController
@RequestMapping("/api/v1/admin/roles")
public class RoleRestController {

    private final RoleRepository roleRepository;

    public RoleRestController(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @GetMapping
    public List<Role> getAllRoles() {
        return this.roleRepository.findAll();
    }
}
