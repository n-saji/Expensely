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

