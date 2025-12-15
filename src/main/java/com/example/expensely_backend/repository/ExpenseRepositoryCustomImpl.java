package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Expense;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.*;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
        System.out.println(customSortBy + " " + customSortOrder);
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
                System.out.println(customSortBy + " " + customSortOrder + " 11");
            } else {
                orders.add(cb.desc(expense.get(customSortBy)));
                System.out.println(customSortBy + " " + customSortOrder + " 12");
            }
            System.out.println(customSortBy + " " + customSortOrder + " 23");
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
}


