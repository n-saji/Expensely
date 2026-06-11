package com.example.expensely_backend.repository;

import com.example.expensely_backend.dto.DailyExpense;
import com.example.expensely_backend.dto.MonthlyCategoryExpense;
import com.example.expensely_backend.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

	Expense findByUserIdAndId(UUID userId, UUID expenseId);

	List<Expense> findByUserId(UUID userId);

	List<Expense> findByCategoryIdAndUserId(UUID categoryId, UUID userId);

	@Query("SELECT e from Expense e where e.user.id = :userId and e.expenseDate >= :startDate and e.expenseDate < :endDate order by e.expenseDate DESC")
	List<Expense> findByUserIdAndTimeFrameDesc(
			@Param("userId") UUID userId,
			@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate
	);

	@Query("SELECT e from Expense e where e.user.id = :userId and e.expenseDate >= :startDate and e.expenseDate < :endDate order by e.expenseDate ASC")
	List<Expense> findByUserIdAndTimeFrameAsc(
			@Param("userId") UUID userId,
			@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate
	);


	@Query(value = """
			 SELECT\s
			     TO_CHAR(e.expense_date, 'FMMonth') AS month,\s
			       c."name" AS categoryName, \s
						     SUM(e.base_currency_amount) AS totalAmount\s
			 FROM expenses e\s
			 JOIN categories c on e.category_id  = c.id\s
			 WHERE e.user_id = :userId\s
			    AND  e.expense_date >= :startDate and e.expense_date < :endDate
			
			 GROUP BY month,c."name"\s
			 ORDER BY MIN(e.expense_date)
			\s""", nativeQuery = true)
	List<MonthlyCategoryExpense> findMonthlyCategoryExpenseByUserId(@Param("userId") UUID userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

	@Query(value = """
			    SELECT
			        TO_CHAR(e.expense_date, 'YYYY-MM-DD') AS expenseDate,
						        SUM(e.base_currency_amount) AS totalAmount
			    FROM expenses e
			    WHERE e.user_id = :userId
			      AND e.expense_date >= :startDate
			      AND e.expense_date < :endDate
			    GROUP BY expenseDate
			    ORDER BY expenseDate
			""", nativeQuery = true)
	List<DailyExpense> findDailyExpenseByUserIdAndTimeFrame(@Param("userId") UUID userId,
	                                                        @Param("startDate") LocalDateTime startDate,
	                                                        @Param("endDate") LocalDateTime endDate);

	@Query(value = """
			SELECT sum(e.base_currency_amount)
			            from expenses e
			            where e.user_id = :userId
			            AND e.expense_date >= :startDate
			            AND e.expense_date <= :endDate
			""", nativeQuery = true)
	Double getTotalExpenseByUserId(UUID userId, LocalDateTime startDate, LocalDateTime endDate);

	@Query("SELECT e FROM Expense e WHERE e.currency IS NULL OR e.currency = '' OR e.baseCurrencyAmount IS NULL OR e.exchangeRate IS NULL OR e.baseCurrency IS NULL")
	List<Expense> findExpensesMissingCurrencySnapshot();

	@Modifying
	@Query("DELETE FROM Expense e WHERE e.user.id = :userId AND e.category.id = :categoryId")
	void deleteByUserIdAndCategoryId(UUID userId, UUID categoryId);

	void deleteAllByUserId(UUID userId);
}

