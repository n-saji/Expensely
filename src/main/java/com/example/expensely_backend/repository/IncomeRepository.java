package com.example.expensely_backend.repository;

import com.example.expensely_backend.dto.DailyIncome;
import com.example.expensely_backend.dto.MonthlyCategoryIncome;
import com.example.expensely_backend.model.Income;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface IncomeRepository extends JpaRepository<Income, UUID> {

	List<Income> findByUserId(UUID userId);

	List<Income> findByCategoryIdAndUserId(UUID categoryId, UUID userId);

	@Query("SELECT i from Income i where i.user.id = :userId and i.incomeDate >= :startDate and i.incomeDate < :endDate order by i.incomeDate DESC")
	List<Income> findByUserIdAndTimeFrameDesc(
			@Param("userId") UUID userId,
			@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate
	);

	@Query("SELECT i from Income i where i.user.id = :userId and i.incomeDate >= :startDate and i.incomeDate < :endDate order by i.incomeDate ASC")
	List<Income> findByUserIdAndTimeFrameAsc(
			@Param("userId") UUID userId,
			@Param("startDate") LocalDateTime startDate,
			@Param("endDate") LocalDateTime endDate
	);

	@Query(value = """
				 SELECT
				     TO_CHAR(i.income_date, 'FMMonth') AS month,
				     c."name" AS categoryName,
				     SUM(i.base_currency_amount) AS totalAmount
				 FROM incomes i
				 JOIN categories c on i.category_id = c.id
				 WHERE i.user_id = :userId
				    AND i.income_date >= :startDate and i.income_date < :endDate
				 GROUP BY month, c."name"
				 ORDER BY MIN(i.income_date)
			""", nativeQuery = true)
	List<MonthlyCategoryIncome> findMonthlyCategoryIncomeByUserId(@Param("userId") UUID userId,
	                                                              @Param("startDate") LocalDateTime startDate,
	                                                              @Param("endDate") LocalDateTime endDate);

	@Query(value = """
			    SELECT
			        TO_CHAR(i.income_date, 'YYYY-MM-DD') AS incomeDate,
			        SUM(i.base_currency_amount) AS totalAmount
			    FROM incomes i
			    WHERE i.user_id = :userId
			      AND i.income_date >= :startDate
			      AND i.income_date < :endDate
			    GROUP BY incomeDate
			    ORDER BY incomeDate
			""", nativeQuery = true)
	List<DailyIncome> findDailyIncomeByUserIdAndTimeFrame(@Param("userId") UUID userId,
	                                                      @Param("startDate") LocalDateTime startDate,
	                                                      @Param("endDate") LocalDateTime endDate);

	@Query(value = """
				SELECT sum(i.base_currency_amount)
				            from incomes i
				            where i.user_id = :userId
				            AND i.income_date >= :startDate
				            AND i.income_date <= :endDate
			""", nativeQuery = true)
	Double getTotalIncomeByUserId(@Param("userId") UUID userId,
	                              @Param("startDate") LocalDateTime startDate,
	                              @Param("endDate") LocalDateTime endDate);

	@Query("SELECT i FROM Income i WHERE i.currency IS NULL OR i.currency = '' OR i.baseCurrencyAmount IS NULL OR i.exchangeRate IS NULL OR i.baseCurrency IS NULL")
	List<Income> findIncomesMissingCurrencySnapshot();

	Income findFirstByUserIdOrderByIncomeDateAsc(UUID userId);

	@Query("delete from Income i where i.user.id = :userId and i.category.id = :categoryId")
	@Modifying
	void deleteByUserIdAndCategoryId(UUID userId, UUID categoryId);

	void deleteAllByUserId(UUID userId);

	@Query(value = "SELECT CAST(EXTRACT(YEAR FROM i.income_date) AS INTEGER) as year_val, " +
	               "SUM(COALESCE(i.base_currency_amount, i.amount)) as total_val, " +
	               "COUNT(i.id) as count_val " +
	               "FROM incomes i " +
	               "WHERE i.user_id = :userId AND EXTRACT(MONTH FROM i.income_date) = :month " +
	               "GROUP BY EXTRACT(YEAR FROM i.income_date) " +
	               "ORDER BY year_val ASC", nativeQuery = true)
	List<Object[]> findHistoricalMonthlyDataIncome(@Param("userId") UUID userId, @Param("month") int month);
}
