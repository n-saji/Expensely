package com.example.expensely_backend.service;

import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class FinancialTransactionService {
    private final TransactionRepository transactions;
    private final FinancialLedgerService ledger;
    private final CategoryRepository categories;
    private final UserRepository users;
    private final ExchangeRateService exchangeRates;
    private final BudgetService budgets;
    private final TransactionService legacyTransactions;
    private final DebtRepository debts;

    public Transaction owned(UUID id, UUID userId) {
        Transaction t=transactions.findById(id).orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if(!t.getUser().getId().equals(userId))throw new IllegalArgumentException("Transaction not found");
        return t;
    }
    public boolean linked(TransactionType type) {
        return type==TransactionType.TRANSFER || type==TransactionType.DEBT_PAYMENT ||
               type==TransactionType.SAVINGS_TRANSFER || type==TransactionType.INVESTMENT_TRANSFER;
    }
    private void sameCurrency(Account a,Account b) {
        if(!a.getCurrency().equals(b.getCurrency()))throw new IllegalArgumentException("Account currencies must match");
    }
    private Transaction validate(Transaction t,UUID userId) {
        if(t.getType()==null || !linked(t.getType()))throw new IllegalArgumentException("Invalid linked transaction type");
        if(t.getAmount()==null || t.getAmount().signum()<=0)throw new IllegalArgumentException("Amount must be positive");
        if(t.getAccount()==null || t.getAccount().getId()==null)throw new IllegalArgumentException("Source account is required");
        Account source=ledger.account(t.getAccount().getId(),userId);
        if(!source.isActive())throw new IllegalArgumentException("Source account is archived");
        if(t.getCurrency()!=null&&!t.getCurrency().equalsIgnoreCase(source.getCurrency()))throw new IllegalArgumentException("Transaction and account currencies must match");
        t.setAccount(source);
        if(t.getTransactionDate()==null)t.setTransactionDate(LocalDateTime.now());
        if(t.getDescription()==null)t.setDescription(t.getType().name().replace('_',' '));
        t.setReceiptUrl(null);
        t.setCurrency(source.getCurrency());
        t.setBaseCurrency(globals.BASE_CURRENCY);
        t.setExchangeRate(exchangeRates.getUsdToCurrencyRate(source.getCurrency()));
        t.setBaseCurrencyAmount(exchangeRates.convertToUsd(t.getAmount(),source.getCurrency()));

        if(t.getType()==TransactionType.TRANSFER) {
            if(t.getDestinationAccount()==null||t.getDestinationAccount().getId()==null)throw new IllegalArgumentException("Destination account is required");
            Account target=ledger.account(t.getDestinationAccount().getId(),userId);
            if(!target.isActive()||target.getId().equals(source.getId()))throw new IllegalArgumentException("Destination account must be active and different");
            sameCurrency(source,target);t.setDestinationAccount(target);
            reject(t.getCategory(),t.getDebt(),t.getSavingsGoal(),t.getInvestmentAccount(),t.getPrincipalAmount(),t.getInterestAmount());
        } else if(t.getType()==TransactionType.DEBT_PAYMENT) {
            if(t.getDebt()==null||t.getDebt().getId()==null)throw new IllegalArgumentException("Debt is required");
            Debt debt=debts.lockForPayment(t.getDebt().getId(),userId).orElseThrow(() -> new IllegalArgumentException("Debt not found"));
            if(!debt.isActive()||!debt.getCurrency().equals(source.getCurrency()))throw new IllegalArgumentException("Debt must be active and match account currency");
            t.setDebt(debt);
            if(t.getSavingsGoal()!=null||t.getInvestmentAccount()!=null)throw new IllegalArgumentException("Unrelated financial entity");
            if(t.getPrincipalAmount()==null&&t.getInterestAmount()==null) {
                t.setPrincipalAmount(t.getAmount());t.setInterestAmount(BigDecimal.ZERO);
            } else if(t.getPrincipalAmount()==null||t.getInterestAmount()==null||t.getPrincipalAmount().signum()<0||t.getInterestAmount().signum()<0||t.getPrincipalAmount().add(t.getInterestAmount()).compareTo(t.getAmount())!=0) {
                throw new IllegalArgumentException("Principal plus interest must equal the payment");
            }
            if(debt.getLinkedAccount()!=null) {
                Account card=debt.getLinkedAccount();sameCurrency(source,card);
                if(t.getDestinationAccount()!=null&&!card.getId().equals(t.getDestinationAccount().getId()))throw new IllegalArgumentException("Destination must be the linked credit card");
                if(card.getId().equals(source.getId()))throw new IllegalArgumentException("Cannot pay a card from itself");
                t.setDestinationAccount(card);
            } else if(t.getDestinationAccount()!=null)throw new IllegalArgumentException("Destination account is only for linked credit card debt");
            if(t.getPrincipalAmount().compareTo(ledger.debtBalance(debt))>0 && t.getId()==null)throw new IllegalArgumentException("Principal exceeds debt balance");
            if(t.getCategory()!=null) {
                if(t.getInterestAmount().signum()==0)throw new IllegalArgumentException("Interest category requires interest");
                Category c=categories.findById(t.getCategory().getId()).orElseThrow(() -> new IllegalArgumentException("Category not found"));
                if(!c.getUser().getId().equals(userId)||!"expense".equalsIgnoreCase(c.getType()))throw new IllegalArgumentException("Interest category must be your expense category");
                t.setCategory(c);
            }
        } else if(t.getType()==TransactionType.SAVINGS_TRANSFER) {
            if(t.getSavingsGoal()==null||t.getSavingsGoal().getId()==null)throw new IllegalArgumentException("Savings goal is required");
            SavingsGoal goal=ledger.goal(t.getSavingsGoal().getId(),userId);
            if(!goal.isActive())throw new IllegalArgumentException("Savings goal is archived");
            Account target=goal.getLinkedAccount();sameCurrency(source,target);
            if(!target.isActive()||(t.getDestinationAccount()!=null&&!target.getId().equals(t.getDestinationAccount().getId())))throw new IllegalArgumentException("Destination must be the active linked savings account");
            if(target.getId().equals(source.getId()))throw new IllegalArgumentException("Savings source and destination must differ");
            t.setSavingsGoal(goal);t.setDestinationAccount(target);
            reject(t.getCategory(),t.getDebt(),null,t.getInvestmentAccount(),t.getPrincipalAmount(),t.getInterestAmount());
        } else {
            if(t.getInvestmentAccount()==null||t.getInvestmentAccount().getId()==null)throw new IllegalArgumentException("Investment account is required");
            InvestmentAccount investment=ledger.investment(t.getInvestmentAccount().getId(),userId);
            if(!investment.isActive())throw new IllegalArgumentException("Investment account is archived");
            Account target=investment.getLinkedAccount();sameCurrency(source,target);
            if(!target.isActive()||(t.getDestinationAccount()!=null&&!target.getId().equals(t.getDestinationAccount().getId())))throw new IllegalArgumentException("Destination must be the active linked brokerage account");
            if(target.getId().equals(source.getId()))throw new IllegalArgumentException("Investment source and destination must differ");
            t.setInvestmentAccount(investment);t.setDestinationAccount(target);
            reject(t.getCategory(),t.getDebt(),t.getSavingsGoal(),null,t.getPrincipalAmount(),t.getInterestAmount());
        }
        return t;
    }
    private void reject(Object category,Object debt,Object goal,Object investment,Object principal,Object interest) {
        if(category!=null||debt!=null||goal!=null||investment!=null||principal!=null||interest!=null)throw new IllegalArgumentException("Invalid fields for transaction type");
    }
    private void budgetInterest(Transaction t,boolean reverse) {
        if(t.getType()!=TransactionType.DEBT_PAYMENT||t.getCategory()==null||t.getInterestAmount()==null||t.getInterestAmount().signum()==0)return;
        BigDecimal base=t.getInterestAmount().divide(t.getExchangeRate(),2,java.math.RoundingMode.HALF_UP);
        if(reverse)base=base.negate();
        budgets.updateBudgetAmountByUserIdAndCategoryId(t.getUser().getId().toString(),t.getCategory().getId().toString(),base,t.getTransactionDate());
    }
    @Transactional public Transaction create(UUID userId,Transaction input) {
        input.setId(null);input.setUser(users.findById(userId).orElseThrow());
        Transaction t=validate(input,userId);
        Transaction saved=transactions.save(t);budgetInterest(saved,false);
        users.markHasTransactions(userId);return saved;
    }
    @Transactional public Transaction update(UUID userId,UUID id,Transaction input) {
        Transaction old=owned(id,userId);
        if(!linked(old.getType())||input.getType()!=old.getType())throw new IllegalArgumentException("Transaction type cannot change");
        budgetInterest(old,true);
        input.setId(id);input.setUser(old.getUser());
        Transaction validated=validate(input,userId);
        if(validated.getType()==TransactionType.DEBT_PAYMENT && validated.getPrincipalAmount().compareTo(ledger.debtBalance(validated.getDebt()).add(validated.getDebt().getId().equals(old.getDebt().getId()) ? (old.getPrincipalAmount()==null?old.getAmount():old.getPrincipalAmount()) : BigDecimal.ZERO))>0) {
            throw new IllegalArgumentException("Principal exceeds debt balance");
        }
        old.setAmount(validated.getAmount());old.setAccount(validated.getAccount());old.setDestinationAccount(validated.getDestinationAccount());
        old.setDebt(validated.getDebt());old.setSavingsGoal(validated.getSavingsGoal());old.setInvestmentAccount(validated.getInvestmentAccount());
        old.setCategory(validated.getCategory());old.setPrincipalAmount(validated.getPrincipalAmount());old.setInterestAmount(validated.getInterestAmount());
        old.setCurrency(validated.getCurrency());old.setBaseCurrency(validated.getBaseCurrency());old.setBaseCurrencyAmount(validated.getBaseCurrencyAmount());old.setExchangeRate(validated.getExchangeRate());
        old.setTransactionDate(validated.getTransactionDate());old.setDescription(validated.getDescription());old.setNotes(validated.getNotes());
        Transaction saved=transactions.save(old);budgetInterest(saved,false);return saved;
    }
    @Transactional public void delete(UUID userId,UUID id) {
        Transaction t=owned(id,userId);budgetInterest(t,true);transactions.delete(t);
    }
    @Transactional public void bulkDelete(UUID userId,java.util.List<Transaction> requests) {
        for(Transaction request:requests) {
            Transaction t=owned(request.getId(),userId);
            if(linked(t.getType()))delete(userId,t.getId());
            else legacyTransactions.deleteTransactionById(t.getId().toString());
        }
    }
}
