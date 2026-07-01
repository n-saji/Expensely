package com.example.expensely_backend.repository;

import com.example.expensely_backend.dto.*;
import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

	Transaction findByUserIdAndIdAndType(UUID userId, UUID id, TransactionType type);

	List<Transaction> findByUserIdAndType(UUID userId, TransactionType type);

	List<Transaction> findByCategoryIdAndUserIdAndType(UUID categoryId, UUID userId, TransactionType type);

	@Query("SELECT t from Transaction t where t.user.id = :userId and t.type = :type and t.transactionDate >= :startDate and t.transactionDate < :endDate order by t.transactionDate DESC")
	List<Transaction> findByUserIdAndTypeAndTimeFrameDesc(
			@Param("userId") UUID userId,
			@Param("type") TransactionType type,
			@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate
	);

	@Query("SELECT t from Transaction t where t.user.id = :userId and t.type = :type and t.transactionDate >= :startDate and t.transactionDate < :endDate order by t.transactionDate ASC")
	List<Transaction> findByUserIdAndTypeAndTimeFrameAsc(
			@Param("userId") UUID userId,
			@Param("type") TransactionType type,
			@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate
	);

	@Query(value = """
			 SELECT 
			     TO_CHAR(t.transaction_date, 'FMMonth') AS month, 
			       c."name" AS categoryName,  
						     SUM(t.base_currency_amount) AS totalAmount 
			 FROM transactions t 
			 JOIN categories c on t.category_id  = c.id 
			 WHERE t.user_id = :userId AND t.type = 'EXPENSE'
			    AND  t.transaction_date >= :startDate and t.transaction_date < :endDate
			
			 GROUP BY month,c."name" 
			 ORDER BY MIN(t.transaction_date)
			""", nativeQuery = true)
	List<MonthlyCategoryExpense> findMonthlyCategoryExpenseByUserId(@Param("userId") UUID userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

	@Query(value = """
				 SELECT
				     TO_CHAR(t.transaction_date, 'FMMonth') AS month,
				     c."name" AS categoryName,
				     SUM(t.base_currency_amount) AS totalAmount
				 FROM transactions t
				 JOIN categories c on t.category_id = c.id
				 WHERE t.user_id = :userId AND t.type = 'INCOME'
				    AND t.transaction_date >= :startDate and t.transaction_date < :endDate
				 GROUP BY month, c."name"
				 ORDER BY MIN(t.transaction_date)
			""", nativeQuery = true)
	List<MonthlyCategoryIncome> findMonthlyCategoryIncomeByUserId(@Param("userId") UUID userId,
	                                                              @Param("startDate") LocalDateTime startDate,
	                                                              @Param("endDate") LocalDateTime endDate);

	@Query(value = """
			    SELECT
			        TO_CHAR(t.transaction_date, 'YYYY-MM-DD') AS expenseDate,
						        SUM(t.base_currency_amount) AS totalAmount
			    FROM transactions t
			    WHERE t.user_id = :userId AND t.type = 'EXPENSE'
			      AND t.transaction_date >= :startDate
			      AND t.transaction_date < :endDate
			    GROUP BY expenseDate
			    ORDER BY expenseDate
			""", nativeQuery = true)
	List<DailyExpense> findDailyExpenseByUserIdAndTimeFrame(@Param("userId") UUID userId,
	                                                        @Param("startDate") LocalDateTime startDate,
	                                                        @Param("endDate") LocalDateTime endDate);

	@Query(value = """
			    SELECT
			        TO_CHAR(t.transaction_date, 'YYYY-MM-DD') AS incomeDate,
			        SUM(t.base_currency_amount) AS totalAmount
			    FROM transactions t
			    WHERE t.user_id = :userId AND t.type = 'INCOME'
			      AND t.transaction_date >= :startDate
			      AND t.transaction_date < :endDate
			    GROUP BY incomeDate
			    ORDER BY incomeDate
			""", nativeQuery = true)
	List<DailyIncome> findDailyIncomeByUserIdAndTimeFrame(@Param("userId") UUID userId,
	                                                      @Param("startDate") LocalDateTime startDate,
	                                                      @Param("endDate") LocalDateTime endDate);

	@Query(value = """
			SELECT sum(t.base_currency_amount)
			            from transactions t
			            where t.user_id = :userId AND t.type = :type
			            AND t.transaction_date >= :startDate
			            AND t.transaction_date <= :endDate
			""", nativeQuery = true)
	Double getTotalAmountByUserId(@Param("userId") UUID userId, @Param("type") String type, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

	@Query("SELECT t FROM Transaction t WHERE t.type = :type AND (t.currency IS NULL OR t.currency = '' OR t.baseCurrencyAmount IS NULL OR t.exchangeRate IS NULL OR t.baseCurrency IS NULL)")
	List<Transaction> findTransactionsMissingCurrencySnapshot(@Param("type") TransactionType type);

	Transaction findFirstByUserIdAndTypeOrderByTransactionDateAsc(UUID userId, TransactionType type);

	@Modifying
	@Query("DELETE FROM Transaction t WHERE t.user.id = :userId AND t.category.id = :categoryId AND t.type = :type")
	void deleteByUserIdAndCategoryIdAndType(@Param("userId") UUID userId, @Param("categoryId") UUID categoryId, @Param("type") TransactionType type);

	@Modifying
	@Query("DELETE FROM Transaction t WHERE t.user.id = :userId")
	void deleteAllByUserId(@Param("userId") UUID userId);

	@Query(value = "SELECT CAST(EXTRACT(YEAR FROM t.transaction_date) AS INTEGER) as year_val, " +
	               "SUM(COALESCE(t.base_currency_amount, t.amount)) as total_val, " +
	               "COUNT(t.id) as count_val " +
	               "FROM transactions t " +
	               "WHERE t.user_id = :userId AND t.type = :type AND EXTRACT(MONTH FROM t.transaction_date) = :month " +
	               "GROUP BY EXTRACT(YEAR FROM t.transaction_date) " +
	               "ORDER BY year_val ASC", nativeQuery = true)
	List<Object[]> findHistoricalMonthlyData(@Param("userId") UUID userId, @Param("month") int month, @Param("type") String type);
}
