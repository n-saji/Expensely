package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {
    List<SavingsGoal> findByUserIdOrderByNameAsc(UUID userId);
}
