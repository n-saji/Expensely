package com.example.expensely_backend.repository;

import com.example.expensely_backend.dto.MonthlyCategoryIncome;
import com.example.expensely_backend.model.Income;
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
public class IncomeRepositoryCustomImpl {

	@PersistenceContext
	private EntityManager em;

	public LinkedHashMap<String, Double> getMonthlyIncomeFromTillTo(
			UUID userId,
			LocalDateTime startDate,
			LocalDateTime endDate) {

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<Income> income = query.from(Income.class);

		Expression<LocalDateTime> monthExpr =
				cb.function("date_trunc", LocalDateTime.class,
						cb.literal("month"),
						income.get("incomeDate"));

		Expression<String> formattedMonth =
				cb.function("to_char", String.class,
						monthExpr,
						cb.literal("FMMon/YY"));

		query.multiselect(
				formattedMonth.alias("month"),
				cb.sum(income.get("amount")).alias("total")
		);

		query.where(
				cb.equal(income.get("user").get("id"), userId),
				cb.greaterThanOrEqualTo(income.get("incomeDate"), startDate),
				cb.lessThan(income.get("incomeDate"), endDate)
		);

		query.groupBy(monthExpr);
		query.orderBy(cb.asc(monthExpr));

		List<Tuple> results = em.createQuery(query).getResultList();

		LinkedHashMap<String, Double> monthlyIncome = new LinkedHashMap<>();
		for (Tuple tuple : results) {
			String month = tuple.get("month", String.class);
			Number totalNumber = tuple.get("total", Number.class);
			Double total = totalNumber != null ? totalNumber.doubleValue() : 0.0;
			monthlyIncome.put(month, total);
		}

		return monthlyIncome;
	}

	public List<Income> findIncomes(
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
		CriteriaQuery<Income> query = cb.createQuery(Income.class);
		Root<Income> income = query.from(Income.class);

		List<Predicate> predicates = new ArrayList<>();
		predicates.add(cb.equal(income.get("user").get("id"), userId));
		predicates.add(cb.greaterThanOrEqualTo(income.get("incomeDate"), startDate));
		predicates.add(cb.lessThan(income.get("incomeDate"), endDate));

		if (categoryId != null) {
			predicates.add(cb.equal(income.get("category").get("id"), categoryId));
		}

		if (q != null && !q.isEmpty()) {
			predicates.add(cb.like(cb.lower(income.get("description")), "%" + q.toLowerCase() + "%"));
		}

		query.select(income).where(predicates.toArray(new Predicate[0]));

		List<Order> orders = new ArrayList<>();
		if (customSortBy != null && !customSortBy.isEmpty()) {
			if (customSortOrder != null && customSortOrder.equalsIgnoreCase("asc")) {
				orders.add(cb.asc(income.get(customSortBy)));
			} else {
				orders.add(cb.desc(income.get(customSortBy)));
			}
		}
		if (defaultSortBy != null && !defaultSortBy.isEmpty()) {
			if (defaultSortBy.equals("desc")) {
				orders.add(cb.desc(income.get("incomeDate")));
			} else {
				orders.add(cb.asc(income.get("incomeDate")));
			}
		}

		query.orderBy(orders);
		return em.createQuery(query)
				.setFirstResult(offset)
				.setMaxResults(limit)
				.getResultList();
	}

	public long countIncomes(
			UUID userId,
			LocalDateTime startDate,
			LocalDateTime endDate,
			UUID categoryId,
			String q
	) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> query = cb.createQuery(Long.class);
		Root<Income> income = query.from(Income.class);

		List<Predicate> predicates = new ArrayList<>();
		predicates.add(cb.equal(income.get("user").get("id"), userId));
		predicates.add(cb.greaterThanOrEqualTo(income.get("incomeDate"), startDate));
		predicates.add(cb.lessThan(income.get("incomeDate"), endDate));

		if (categoryId != null) {
			predicates.add(cb.equal(income.get("category").get("id"), categoryId));
		}

		if (q != null && !q.isEmpty()) {
			predicates.add(cb.like(cb.lower(income.get("description")), "%" + q.toLowerCase() + "%"));
		}

		query.select(cb.count(income))
				.where(predicates.toArray(new Predicate[0]));

		return em.createQuery(query).getSingleResult();
	}

	public List<MonthlyCategoryIncome> getMonthlyCategoryIncomeFromTillTo(
			UUID userId,
			LocalDateTime startDate,
			LocalDateTime endDate) {

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<Income> income = query.from(Income.class);
		Join<Object, Object> category = income.join("category", JoinType.LEFT);

		Expression<LocalDateTime> monthExpr =
				cb.function("date_trunc", LocalDateTime.class,
						cb.literal("month"),
						income.get("incomeDate"));

		Expression<String> formattedMonth =
				cb.function("to_char", String.class,
						monthExpr,
						cb.literal("FMMon/YY"));

		query.multiselect(
				formattedMonth.alias("month"),
				category.get("name").alias("category"),
				cb.sum(income.get("amount")).alias("total")
		);

		query.where(
				cb.equal(income.get("user").get("id"), userId),
				cb.greaterThanOrEqualTo(income.get("incomeDate"), startDate),
				cb.lessThan(income.get("incomeDate"), endDate)
		);

		query.groupBy(monthExpr, category.get("name"));
		query.orderBy(cb.asc(monthExpr));
		List<Tuple> results = em.createQuery(query).getResultList();

		List<MonthlyCategoryIncome> monthlyCategoryIncome = new ArrayList<>();
		for (Tuple tuple : results) {
			String month = tuple.get("month", String.class);
			String categoryName = tuple.get("category", String.class);
			Number totalNumber = tuple.get("total", Number.class);
			Double total = totalNumber != null ? totalNumber.doubleValue() : 0.0;
			monthlyCategoryIncome.add(new MonthlyCategoryIncome() {
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

		return monthlyCategoryIncome;
	}
}

