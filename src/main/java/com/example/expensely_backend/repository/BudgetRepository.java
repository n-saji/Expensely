package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    Boolean existsByCategoryId(UUID categoryId);

    List<Budget> findByCategoryId(UUID categoryId);

    List<Budget> findByUserId(UUID userId);

    List<Budget> findByUserIdAndCategoryId(UUID userId, UUID categoryId);

    Boolean existsByUserIdAndCategoryId(UUID userId, UUID categoryId);
}
