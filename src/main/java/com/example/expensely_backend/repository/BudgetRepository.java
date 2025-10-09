package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
    Boolean existsByCategoryId(UUID categoryId);

    @Query("SELECT b FROM Budget b WHERE b.category.id = ?1 and b.isActive = true")
    List<Budget> findByCategoryId(UUID categoryId);

    @Query("SELECT b FROM Budget b WHERE b.user.id = ?1 and b.isActive = true")
    List<Budget> findByUserId(UUID userId);

    @Query("SELECT b FROM Budget b WHERE b.user.id = ?1 and b.category.id = ?2 and b.isActive = true")
    Budget findByUserIdAndCategoryId(UUID userId, UUID categoryId);

    Boolean existsByUserIdAndCategoryIdAndIsActiveTrue(UUID userId, UUID categoryId);

    List<Budget> findByEndDateBeforeAndIsActiveTrue(LocalDate today);

    long deleteAllByUserId(UUID userId);
}
