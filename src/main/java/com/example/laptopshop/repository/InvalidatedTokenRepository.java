package com.example.laptopshop.repository;

import org.springframework.data.repository.CrudRepository;

import com.example.laptopshop.domain.InvalidatedToken;

// Redis Repository dùng CrudRepository, KHÔNG dùng JpaRepository như các repo còn lại
public interface InvalidatedTokenRepository extends CrudRepository<InvalidatedToken, String> {
}