package com.example.laptopshop.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.laptopshop.domain.Permission;

public interface PermissionRepository extends JpaRepository<Permission, String> {
    Optional<Permission> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, String id);
}