package com.example.expensely_backend.repository;

import com.example.expensely_backend.dto.MonthlyCategoryExpense;
import com.example.expensely_backend.model.Expense;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Repository
public class ExpenseRepositoryCustomImpl {

    @PersistenceContext
    private EntityManager em;

    public List<Expense> findExpenses(
            UUID userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID categoryId,
            String q,
            int offset,
            int limit,
            String customSortBy,
            String customSortOrder,
            String defaultSortBy
    ) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Expense> query = cb.createQuery(Expense.class);
        Root<Expense> expense = query.from(Expense.class);

        List<Predicate> predicates = new ArrayList<>();

        // Mandatory filters
        predicates.add(cb.equal(expense.get("user").get("id"), userId));
        predicates.add(cb.greaterThanOrEqualTo(expense.get("expenseDate"), startDate));
        predicates.add(cb.lessThan(expense.get("expenseDate"), endDate));

        // Optional category filter
        if (categoryId != null) {
            predicates.add(cb.equal(expense.get("category").get("id"), categoryId));
        }

        // Optional description filter (ILIKE)
        if (q != null && !q.isEmpty()) {
            predicates.add(cb.like(cb.lower(expense.get("description")), "%" + q.toLowerCase() + "%"));
        }


        query.select(expense)
                .where(predicates.toArray(new Predicate[0]))
        ;

        List<Order> orders = new ArrayList<>();

        if (customSortBy != null && !customSortBy.isEmpty()) {

            if (customSortOrder != null && customSortOrder.equalsIgnoreCase("asc")) {
                orders.add(cb.asc(expense.get(customSortBy)));
            } else {
                orders.add(cb.desc(expense.get(customSortBy)));
            }
        }
        if (defaultSortBy != null && !defaultSortBy.isEmpty()) {
            if (defaultSortBy.equals("desc")) {
                orders.add(cb.desc(expense.get("expenseDate")));
            } else {
                orders.add(cb.asc(expense.get("expenseDate")));
            }
        }

        query.orderBy(orders);
        return em.createQuery(query)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList();
    }

    public long countExpenses(
            UUID userId,
            LocalDateTime startDate,
            LocalDateTime endDate,
            UUID categoryId,
            String q
    ) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Long> query = cb.createQuery(Long.class);
        Root<Expense> expense = query.from(Expense.class);

        // Create a list of predicates (conditions)
        List<Predicate> predicates = new ArrayList<>();

        // Mandatory conditions
        predicates.add(cb.equal(expense.get("user").get("id"), userId));
        predicates.add(cb.greaterThanOrEqualTo(expense.get("expenseDate"), startDate));
        predicates.add(cb.lessThan(expense.get("expenseDate"), endDate));

        // Optional category filter
        if (categoryId != null) {
            predicates.add(cb.equal(expense.get("category").get("id"), categoryId));
        }

        // Optional description filter (ILIKE)
        if (q != null && !q.isEmpty()) {
            predicates.add(cb.like(cb.lower(expense.get("description")), "%" + q.toLowerCase() + "%"));
        }

        // Build the count query
        query.select(cb.count(expense))
                .where(predicates.toArray(new Predicate[0]));

        return em.createQuery(query).getSingleResult();
    }

    public LinkedHashMap<String, Double> getMonthlyExpenseFromTillTo(
            UUID userId,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Expense> expense = query.from(Expense.class);

        Expression<LocalDateTime> monthExpr =
                cb.function("date_trunc", LocalDateTime.class,
                        cb.literal("month"),
                        expense.get("expenseDate"));

        Expression<String> formattedMonth =
                cb.function("to_char", String.class,
                        monthExpr,
                        cb.literal("FMMon/YY"));

        query.multiselect(
                formattedMonth.alias("month"),
                cb.sum(expense.get("amount")).alias("total")
        );

        query.where(
                cb.equal(expense.get("user").get("id"), userId),
                cb.greaterThanOrEqualTo(expense.get("expenseDate"), startDate),
                cb.lessThan(expense.get("expenseDate"), endDate)
        );

        query.groupBy(monthExpr);
        query.orderBy(cb.desc(monthExpr));

        List<Tuple> results = em.createQuery(query).getResultList();

        LinkedHashMap<String, Double> monthlyExpenses = new LinkedHashMap<>();
        for (Tuple tuple : results) {
            String month = tuple.get("month", String.class);
            Number totalNumber = tuple.get("total", Number.class);
            Double total = totalNumber != null ? totalNumber.doubleValue() : 0.0;
            monthlyExpenses.put(month, total);
        }

        return monthlyExpenses;
    }

    public List<MonthlyCategoryExpense> getMonthlyCategoryExpenseFromTillTo(
            UUID userId,
            LocalDateTime startDate,
            LocalDateTime endDate) {

        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Expense> expense = query.from(Expense.class);
        Join<Object, Object> category = expense.join("category", JoinType.LEFT);

        Expression<LocalDateTime> monthExpr =
                cb.function("date_trunc", LocalDateTime.class,
                        cb.literal("month"),
                        expense.get("expenseDate"));

        Expression<String> formattedMonth =
                cb.function("to_char", String.class,
                        monthExpr,
                        cb.literal("FMMon/YY"));

        query.multiselect(
                formattedMonth.alias("month"),
                category.get("name").alias("category"),
                cb.sum(expense.get("amount")).alias("total")
        );


        query.where(
                cb.equal(expense.get("user").get("id"), userId),
                cb.greaterThanOrEqualTo(expense.get("expenseDate"), startDate),
                cb.lessThan(expense.get("expenseDate"), endDate)
        );

        query.groupBy(monthExpr, category.get("name"));
        query.orderBy(cb.desc(monthExpr));
        List<Tuple> results = em.createQuery(query).getResultList();

        List<MonthlyCategoryExpense> monthlyCategoryExpenses = new ArrayList<>();
        for (Tuple tuple : results) {
            String month = tuple.get("month", String.class);
            String categoryName = tuple.get("category", String.class);
            Number totalNumber = tuple.get("total", Number.class);
            Double total = totalNumber != null ? totalNumber.doubleValue() : 0.0;
            monthlyCategoryExpenses.add(new MonthlyCategoryExpense() {
                @Override
                public String getMonth() {
                    return month;
                }

                @Override
                public String getCategoryName() {
                    return categoryName;
                }

                @Override
                public Double getTotalAmount() {
                    return total;
                }
            });
        }

        return monthlyCategoryExpenses;
    }

}



