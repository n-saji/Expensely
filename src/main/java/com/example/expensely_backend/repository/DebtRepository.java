package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Debt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface DebtRepository extends JpaRepository<Debt, UUID> {
    List<Debt> findByUserIdOrderByNameAsc(UUID userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Debt d where d.id=:id and d.user.id=:userId")
    Optional<Debt> lockForPayment(@Param("id") UUID id,@Param("userId") UUID userId);
}
