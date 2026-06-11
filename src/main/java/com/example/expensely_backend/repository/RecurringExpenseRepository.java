package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {
	List<RecurringExpense> findByActiveTrueAndNextOccurrence(LocalDate nextOccurrence);

	List<RecurringExpense> findByUserIdOrderByCreatedAtDesc(UUID userId);

	List<RecurringExpense> findByCurrencyIsNullOrCurrencyEquals(String currency);

	List<RecurringExpense> findByCategoryIdAndUserId(UUID cId, UUID uId);

	@Modifying
	@Query("DELETE FROM RecurringExpense re WHERE re.user.id = :userId AND re.category.id = :categoryId")
	void deleteByUserIdAndCategoryId(UUID userId, UUID categoryId);

	void deleteAllByUserId(UUID userId);
}
