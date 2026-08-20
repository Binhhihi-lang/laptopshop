package com.example.laptopshop.repository;

import org.springframework.data.repository.CrudRepository;

import com.example.laptopshop.domain.CachedAuthorities;

// Redis Repository dùng CrudRepository (giống InvalidatedTokenRepository /
// RefreshTokenRepository), KHÔNG dùng JpaRepository.
public interface CachedAuthoritiesRepository extends CrudRepository<CachedAuthorities, String> {
}
