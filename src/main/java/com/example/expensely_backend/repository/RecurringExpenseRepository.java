package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {
	List<RecurringExpense> findByActiveTrueAndNextOccurrence(LocalDate nextOccurrence);

	List<RecurringExpense> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
