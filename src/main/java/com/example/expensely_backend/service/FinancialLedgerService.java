package com.example.expensely_backend.service;

import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.UUID;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service @RequiredArgsConstructor
public class FinancialLedgerService {
    private final AccountRepository accounts;
    private final DebtRepository debts;
    private final SavingsGoalRepository goals;
    private final InvestmentAccountRepository investments;
    private final JdbcTemplate jdbc;

    public Account account(UUID id, UUID userId) {
        Account account = accounts.findById(id).orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!account.getUser().getId().equals(userId)) throw new IllegalArgumentException("Account not found");
        return account;
    }
    public Debt debt(UUID id, UUID userId) {
        Debt debt = debts.findById(id).orElseThrow(() -> new IllegalArgumentException("Debt not found"));
        if (!debt.getUser().getId().equals(userId)) throw new IllegalArgumentException("Debt not found");
        return debt;
    }
    public SavingsGoal goal(UUID id, UUID userId) {
        SavingsGoal goal = goals.findById(id).orElseThrow(() -> new IllegalArgumentException("Savings goal not found"));
        if (!goal.getUser().getId().equals(userId)) throw new IllegalArgumentException("Savings goal not found");
        return goal;
    }
    public InvestmentAccount investment(UUID id, UUID userId) {
        InvestmentAccount investment = investments.findById(id).orElseThrow(() -> new IllegalArgumentException("Investment account not found"));
        if (!investment.getUser().getId().equals(userId)) throw new IllegalArgumentException("Investment account not found");
        return investment;
    }
    private BigDecimal sum(String sql, UUID id) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, id);
        return value == null ? BigDecimal.ZERO : value;
    }
    public BigDecimal accountBalance(Account account) {
        return account.getOpeningBalance().add(sum("""
            SELECT COALESCE(SUM(CASE
              WHEN account_id = ? THEN CASE WHEN type = 'INCOME' THEN amount ELSE -amount END
              WHEN destination_account_id = ? THEN CASE WHEN type = 'DEBT_PAYMENT' THEN COALESCE(principal_amount, amount) ELSE amount END
              ELSE 0 END), 0)
            FROM transactions WHERE account_id = ? OR destination_account_id = ?
            """, account.getId(), account.getId(), account.getId(), account.getId()));
    }
    private BigDecimal sum(String sql, UUID... ids) {
        BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, (Object[]) ids);
        return value == null ? BigDecimal.ZERO : value;
    }
    public BigDecimal debtBalance(Debt debt) {
        if (debt.getLinkedAccount() != null) return accountBalance(debt.getLinkedAccount()).negate().max(BigDecimal.ZERO);
        return debt.getOpeningBalance().subtract(sum("SELECT COALESCE(SUM(COALESCE(principal_amount,amount)),0) FROM transactions WHERE debt_id = ? AND type = 'DEBT_PAYMENT'", debt.getId()));
    }
    public BigDecimal goalBalance(SavingsGoal goal) {
        return goal.getOpeningAmount().add(sum("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE savings_goal_id = ? AND type = 'SAVINGS_TRANSFER'", goal.getId()));
    }
    public BigDecimal investmentContributions(InvestmentAccount investment) {
        return investment.getOpeningContributions().add(sum("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE investment_account_id = ? AND type = 'INVESTMENT_TRANSFER'", investment.getId()));
    }
    public Map<String,BigDecimal> summary(UUID userId,LocalDateTime start,LocalDateTime end) {
        Map<String,BigDecimal> result=new LinkedHashMap<>();
        for(String type:new String[]{"INCOME","EXPENSE","DEBT_PAYMENT","SAVINGS_TRANSFER","INVESTMENT_TRANSFER"}) {
            BigDecimal total=jdbc.queryForObject("""
                SELECT COALESCE(SUM(CASE WHEN type='DEBT_PAYMENT' THEN
                CASE WHEN ?='EXPENSE' THEN base_currency_amount*COALESCE(interest_amount,0)/amount ELSE base_currency_amount*COALESCE(principal_amount,amount)/amount END
                ELSE base_currency_amount END),0)
                FROM transactions WHERE user_id=? AND transaction_date>=? AND transaction_date<?
                AND (type=? OR (?='EXPENSE' AND type='DEBT_PAYMENT'))
                """,BigDecimal.class,type,userId,start,end,type,type);
            String key=switch(type){case "DEBT_PAYMENT"->"debtRepayment";case "SAVINGS_TRANSFER"->"savingsContributions";case "INVESTMENT_TRANSFER"->"investmentContributions";default->type.toLowerCase();};
            result.put(key,total==null?BigDecimal.ZERO:total);
        }
        result.put("netCashFlow",result.get("income").subtract(result.get("expense")));
        return result;
    }
}
