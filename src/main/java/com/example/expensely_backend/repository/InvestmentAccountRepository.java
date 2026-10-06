package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.InvestmentAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface InvestmentAccountRepository extends JpaRepository<InvestmentAccount, UUID> {
    List<InvestmentAccount> findByUserIdOrderByNameAsc(UUID userId);
}
