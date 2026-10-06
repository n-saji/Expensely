package com.example.expensely_backend.repository;

import com.example.expensely_backend.dto.MonthlyCategoryExpense;
import com.example.expensely_backend.dto.MonthlyCategoryIncome;
import com.example.expensely_backend.model.Transaction;
import com.example.expensely_backend.model.TransactionType;
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
public class TransactionRepositoryCustomImpl {

	@PersistenceContext
	private EntityManager em;

	public List<Transaction> findTransactions(
			UUID userId,
			TransactionType type,
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
		CriteriaQuery<Transaction> query = cb.createQuery(Transaction.class);
		Root<Transaction> transaction = query.from(Transaction.class);

		List<Predicate> predicates = new ArrayList<>();

		// Mandatory filters
		predicates.add(cb.equal(transaction.get("user").get("id"), userId));
		if (type != null) {
			predicates.add(cb.equal(transaction.get("type"), type));
		}
		predicates.add(cb.greaterThanOrEqualTo(transaction.get("transactionDate"), startDate));
		predicates.add(cb.lessThan(transaction.get("transactionDate"), endDate));

		// Optional category filter
		if (categoryId != null) {
			predicates.add(cb.equal(transaction.get("category").get("id"), categoryId));
		}

		// Optional description filter (ILIKE)
		if (q != null && !q.isEmpty()) {
			predicates.add(cb.like(cb.lower(transaction.get("description")), "%" + q.toLowerCase() + "%"));
		}

		query.select(transaction)
				.where(predicates.toArray(new Predicate[0]));

		List<Order> orders = new ArrayList<>();

		if (customSortBy != null && !customSortBy.isEmpty()) {
			String sortCol = customSortBy;
			if (customSortBy.equals("expenseDate") || customSortBy.equals("incomeDate")) {
				sortCol = "transactionDate";
			}
			if (customSortOrder != null && customSortOrder.equalsIgnoreCase("asc")) {
				orders.add(cb.asc(transaction.get(sortCol)));
			} else {
				orders.add(cb.desc(transaction.get(sortCol)));
			}
		}
		if (defaultSortBy != null && !defaultSortBy.isEmpty()) {
			if (defaultSortBy.equals("desc")) {
				orders.add(cb.desc(transaction.get("transactionDate")));
			} else {
				orders.add(cb.asc(transaction.get("transactionDate")));
			}
		}

		query.orderBy(orders);
		return em.createQuery(query)
				.setFirstResult(offset)
				.setMaxResults(limit)
				.getResultList();
	}

	public long countTransactions(
			UUID userId,
			TransactionType type,
			LocalDateTime startDate,
			LocalDateTime endDate,
			UUID categoryId,
			String q
	) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> query = cb.createQuery(Long.class);
		Root<Transaction> transaction = query.from(Transaction.class);

		List<Predicate> predicates = new ArrayList<>();

		predicates.add(cb.equal(transaction.get("user").get("id"), userId));
		if (type != null) {
			predicates.add(cb.equal(transaction.get("type"), type));
		}
		predicates.add(cb.greaterThanOrEqualTo(transaction.get("transactionDate"), startDate));
		predicates.add(cb.lessThan(transaction.get("transactionDate"), endDate));

		if (categoryId != null) {
			predicates.add(cb.equal(transaction.get("category").get("id"), categoryId));
		}

		if (q != null && !q.isEmpty()) {
			predicates.add(cb.like(cb.lower(transaction.get("description")), "%" + q.toLowerCase() + "%"));
		}

		query.select(cb.count(transaction))
				.where(predicates.toArray(new Predicate[0]));

		return em.createQuery(query).getSingleResult();
	}

	public LinkedHashMap<String, Double> getMonthlyAmountFromTillTo(
			UUID userId,
			TransactionType type,
			LocalDateTime startDate,
			LocalDateTime endDate) {
		if(type==TransactionType.EXPENSE) {
			@SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
				SELECT TO_CHAR(DATE_TRUNC('month',transaction_date),'FMMon/YY'),
				SUM(CASE WHEN type='DEBT_PAYMENT' THEN base_currency_amount*interest_amount/amount ELSE base_currency_amount END)
				FROM transactions WHERE user_id=:userId AND transaction_date>=:startDate AND transaction_date<:endDate
				AND (type='EXPENSE' OR (type='DEBT_PAYMENT' AND interest_amount>0))
				GROUP BY DATE_TRUNC('month',transaction_date) ORDER BY DATE_TRUNC('month',transaction_date)
				""").setParameter("userId",userId).setParameter("startDate",startDate).setParameter("endDate",endDate).getResultList();
			LinkedHashMap<String,Double> totals=new LinkedHashMap<>();
			for(Object[] row:rows)totals.put((String)row[0],((Number)row[1]).doubleValue());
			return totals;
		}

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<Transaction> transaction = query.from(Transaction.class);

		Expression<LocalDateTime> monthExpr =
				cb.function("date_trunc", LocalDateTime.class,
						cb.literal("month"),
						transaction.get("transactionDate"));

		Expression<String> formattedMonth =
				cb.function("to_char", String.class,
						monthExpr,
						cb.literal("FMMon/YY"));

		query.multiselect(
				formattedMonth.alias("month"),
				cb.sum(transaction.get("baseCurrencyAmount")).alias("total")
		);

		query.where(
				cb.equal(transaction.get("user").get("id"), userId),
				cb.equal(transaction.get("type"), type),
				cb.greaterThanOrEqualTo(transaction.get("transactionDate"), startDate),
				cb.lessThan(transaction.get("transactionDate"), endDate)
		);

		query.groupBy(monthExpr);
		query.orderBy(cb.asc(monthExpr));

		List<Tuple> results = em.createQuery(query).getResultList();

		LinkedHashMap<String, Double> monthlyAmounts = new LinkedHashMap<>();
		for (Tuple tuple : results) {
			String month = tuple.get("month", String.class);
			Number totalNumber = tuple.get("total", Number.class);
			Double total = totalNumber != null ? totalNumber.doubleValue() : 0.0;
			monthlyAmounts.put(month, total);
		}

		return monthlyAmounts;
	}

	public List<MonthlyCategoryExpense> getMonthlyCategoryExpenseFromTillTo(
			UUID userId,
			LocalDateTime startDate,
			LocalDateTime endDate) {
		@SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
			SELECT TO_CHAR(DATE_TRUNC('month',t.transaction_date),'FMMon/YY'),
			COALESCE(c.name,'Loan Interest'),
			SUM(CASE WHEN t.type='DEBT_PAYMENT' THEN t.base_currency_amount*t.interest_amount/t.amount ELSE t.base_currency_amount END)
			FROM transactions t LEFT JOIN categories c ON c.id=t.category_id
			WHERE t.user_id=:userId AND t.transaction_date>=:startDate AND t.transaction_date<:endDate
			AND (t.type='EXPENSE' OR (t.type='DEBT_PAYMENT' AND t.interest_amount>0))
			GROUP BY DATE_TRUNC('month',t.transaction_date),COALESCE(c.name,'Loan Interest')
			ORDER BY DATE_TRUNC('month',t.transaction_date)
			""").setParameter("userId",userId).setParameter("startDate",startDate).setParameter("endDate",endDate).getResultList();
		List<MonthlyCategoryExpense> result=new ArrayList<>();
		for(Object[] row:rows) {
			String month=(String)row[0],categoryName=(String)row[1];Double total=((Number)row[2]).doubleValue();
			result.add(new MonthlyCategoryExpense() {
				public String getMonth(){return month;}
				public String getCategoryName(){return categoryName;}
				public Double getTotalAmount(){return total;}
			});
		}
		return result;
	}

	public List<MonthlyCategoryIncome> getMonthlyCategoryIncomeFromTillTo(
			UUID userId,
			LocalDateTime startDate,
			LocalDateTime endDate) {

		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Tuple> query = cb.createTupleQuery();
		Root<Transaction> transaction = query.from(Transaction.class);
		Join<Object, Object> category = transaction.join("category", JoinType.LEFT);

		Expression<LocalDateTime> monthExpr =
				cb.function("date_trunc", LocalDateTime.class,
						cb.literal("month"),
						transaction.get("transactionDate"));

		Expression<String> formattedMonth =
				cb.function("to_char", String.class,
						monthExpr,
						cb.literal("FMMon/YY"));

		query.multiselect(
				formattedMonth.alias("month"),
				category.get("name").alias("category"),
				cb.sum(transaction.get("baseCurrencyAmount")).alias("total")
		);

		query.where(
				cb.equal(transaction.get("user").get("id"), userId),
				cb.equal(transaction.get("type"), TransactionType.INCOME),
				cb.greaterThanOrEqualTo(transaction.get("transactionDate"), startDate),
				cb.lessThan(transaction.get("transactionDate"), endDate)
		);

		query.groupBy(monthExpr, category.get("name"));
		query.orderBy(cb.asc(monthExpr));
		List<Tuple> results = em.createQuery(query).getResultList();

		List<MonthlyCategoryIncome> monthlyCategoryIncomes = new ArrayList<>();
		for (Tuple tuple : results) {
			String month = tuple.get("month", String.class);
			String categoryName = tuple.get("category", String.class);
			Number totalNumber = tuple.get("total", Number.class);
			Double total = totalNumber != null ? totalNumber.doubleValue() : 0.0;
			monthlyCategoryIncomes.add(new MonthlyCategoryIncome() {
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

		return monthlyCategoryIncomes;
	}
}
