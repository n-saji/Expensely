package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.model.Expense;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {

	@Query("SELECT b FROM Budget b WHERE b.user.id = ?1 and b.isActive = true " +
			"ORDER BY CASE WHEN b.amountLimit IS NULL OR b.amountLimit <= 0 THEN 0 " +
			"ELSE COALESCE(b.amountSpent, 0) / b.amountLimit END DESC, b.updatedAt DESC")
	List<Budget> findActiveBudgetsByUserId(UUID userId);

	@Query("SELECT b FROM Budget b " +
			"ORDER BY CASE WHEN b.amountLimit IS NULL OR b.amountLimit <= 0 THEN 0 " +
			"ELSE COALESCE(b.amountSpent, 0) / b.baseCurrencyAmount END DESC, b.updatedAt DESC")
	List<Budget> findAllOrderByUtilizationDesc();

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT b FROM Budget b WHERE b.user.id = ?1 and b.category.id = ?2 and b.isActive = true")
	Budget findActiveBudgetByUserIdAndCategoryIdForUpdate(UUID userId, UUID categoryId);

	Boolean existsByUserIdAndCategoryIdAndIsActiveTrue(UUID userId, UUID categoryId);

	@Query("SELECT b FROM Budget b WHERE b.endDate < ?1 and b.isActive = true " +
			"ORDER BY CASE WHEN b.amountLimit IS NULL OR b.amountLimit <= 0 THEN 0 " +
			"ELSE COALESCE(b.amountSpent, 0) / b.amountLimit END DESC, b.updatedAt DESC")
	List<Budget> findBudgetByEndDateBeforeAndIsActiveTrue(LocalDate today);

	void deleteAllByUserId(UUID userId);


	@Query("SELECT b FROM Budget b WHERE b.currency IS NULL OR b" +
			".baseCurrencyAmount IS NULL OR b.exchangeRate IS NULL")
	List<Budget> findBudgetMissingCurrencySnapshot();
}
