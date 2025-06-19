package com.example.expensely_backend.repository;

import com.example.expensely_backend.dto.MonthlyCategoryExpense;
import com.example.expensely_backend.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID> {

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

    @Query("SELECT e from Expense e where e.user.id = :userId and e.expenseDate >= :startDate and e.expenseDate < :endDate and e.category.id = :categoryId order by e.expenseDate DESC limit :limit offset :offset")
    List<Expense> findByUserIdAndTimeFrameAndCategoryDescWithLimit(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("categoryId") UUID categoryId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );


    @Query("SELECT e from Expense e where e.user.id = :userId and e.expenseDate >= :startDate and e.expenseDate < :endDate and e.category.id = :categoryId order by e.expenseDate ASC limit :limit offset :offset")
    List<Expense> findByUserIdAndTimeFrameAndCategoryAscWithLimit(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("categoryId") UUID categoryId,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("SELECT e from Expense e where e.user.id = :userId and e.expenseDate >= :startDate and e.expenseDate < :endDate order by e.expenseDate DESC limit :limit offset :offset")
    List<Expense> findByUserIdAndTimeFrameDescWithLimit(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("SELECT e from Expense e where e.user.id = :userId and e.expenseDate >= :startDate and e.expenseDate < :endDate order by e.expenseDate ASC limit :limit offset :offset")
    List<Expense> findByUserIdAndTimeFrameAscWithLimit(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Query("SELECT count(e) from Expense e where e.user.id = :userId and e.expenseDate >= :startDate and e.expenseDate < :endDate and e.category.id = :categoryId")
    int countByUserIdAndTimeFrameAndCategory(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("categoryId") UUID categoryId
    );

    @Query("SELECT count(e) from Expense e where e.user.id = :userId and e.expenseDate >= :startDate and e.expenseDate < :endDate")
    int countByUserIdAndTimeFrame(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query(value = """
             SELECT\s
                 TO_CHAR(e.expense_date, 'FMMonth') AS month,\s
                   c."name" AS categoryName, \s
                 SUM(e.amount) AS totalAmount\s
             FROM expenses e\s
             JOIN categories c on e.category_id  = c.id\s
             WHERE e.user_id = :userId\s
               AND EXTRACT(YEAR FROM e.expense_date) = EXTRACT(YEAR FROM NOW())\s
             GROUP BY month,c."name"\s
             ORDER BY MIN(e.expense_date)
            \s""", nativeQuery = true)
    List<MonthlyCategoryExpense> findMonthlyCategoryExpenseByUserId(@Param("userId") UUID userId);


}

