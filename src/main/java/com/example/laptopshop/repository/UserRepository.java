package com.example.laptopshop.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import com.example.laptopshop.domain.User;

@Repository
public interface UserRepository extends CrudRepository<User, Long>  {
    User save(User chubinh);
}
