package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;


public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findUserByEmailOrPhone(String email, String phone);
}
