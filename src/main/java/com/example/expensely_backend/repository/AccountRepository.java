package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    List<Account> findByUserIdOrderByNameAsc(UUID userId);
}
