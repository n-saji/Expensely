package com.example.expensely_backend.controller;

import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import com.example.expensely_backend.service.FinancialLedgerService;
import com.example.expensely_backend.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.*;
import java.time.LocalDate;

@RestController @RequiredArgsConstructor
public class FinancialController {
    private final UserRepository users;
    private final AccountRepository accounts;
    private final DebtRepository debts;
    private final SavingsGoalRepository goals;
    private final InvestmentAccountRepository investments;
    private final FinancialLedgerService ledger;
    private final TransactionRepository transactions;
    private final TransactionService transactionService;

    private UUID uid(Authentication auth) { return UUID.fromString((String) auth.getPrincipal()); }
    private User user(Authentication auth) { return users.findById(uid(auth)).orElseThrow(); }
    private void positive(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(name + " must be nonnegative");
    }
    private void validCurrency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) throw new IllegalArgumentException("Currency must be a three-letter ISO code");
    }
    private Map<String,Object> accountView(Account a) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", a.getId()); out.put("name", a.getName()); out.put("type", a.getType());
        out.put("currency", a.getCurrency()); out.put("openingBalance", a.getOpeningBalance());
        out.put("balance", ledger.accountBalance(a)); out.put("active", a.isActive()); return out;
    }
    private Map<String,Object> debtView(Debt d) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id", d.getId()); out.put("name", d.getName()); out.put("debtType", d.getDebtType());
        out.put("originalBalance", d.getOriginalBalance()); out.put("openingBalance", d.getOpeningBalance());
        out.put("currentBalance", ledger.debtBalance(d)); out.put("interestRate", d.getInterestRate());
        out.put("minimumPayment", d.getMinimumPayment()); out.put("dueDate", d.getDueDate());
        out.put("lender", d.getLender()); out.put("currency", d.getCurrency()); out.put("active", d.isActive());
        out.put("linkedAccountId", d.getLinkedAccount() == null ? null : d.getLinkedAccount().getId()); return out;
    }
    private Map<String,Object> goalView(SavingsGoal g) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id",g.getId()); out.put("name",g.getName()); out.put("targetAmount",g.getTargetAmount());
        out.put("openingAmount",g.getOpeningAmount()); out.put("currentAmount",ledger.goalBalance(g));
        out.put("targetDate",g.getTargetDate()); out.put("linkedAccountId",g.getLinkedAccount().getId());
        out.put("active",g.isActive()); return out;
    }
    private Map<String,Object> investmentView(InvestmentAccount i) {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("id",i.getId()); out.put("name",i.getName()); out.put("provider",i.getProvider());
        out.put("accountType",i.getAccountType()); out.put("openingContributions",i.getOpeningContributions());
        out.put("contributions",ledger.investmentContributions(i)); out.put("linkedAccountId",i.getLinkedAccount().getId());
        out.put("active",i.isActive()); return out;
    }

    @GetMapping("/api/finances/summary") public Object summary(Authentication auth,
        @RequestParam(required=false) LocalDate start,@RequestParam(required=false) LocalDate end) {
        LocalDate from=start==null?LocalDate.now().withDayOfMonth(1):start;
        LocalDate to=end==null?from.plusMonths(1):end.plusDays(1);
        if(!to.isAfter(from))throw new IllegalArgumentException("End date must follow start date");
        return ledger.summary(uid(auth),from.atStartOfDay(),to.atStartOfDay());
    }

    @GetMapping("/api/accounts") public Object accounts(Authentication auth) {
        return accounts.findByUserIdOrderByNameAsc(uid(auth)).stream().map(this::accountView).toList();
    }
    @GetMapping("/api/accounts/{id}") public Object account(Authentication auth, @PathVariable UUID id) { return accountView(ledger.account(id,uid(auth))); }
    @PostMapping("/api/accounts") @Transactional public Object createAccount(Authentication auth, @RequestBody Account a) {
        a.setId(null); a.setUser(user(auth));
        if (a.getName() == null || a.getName().isBlank()) throw new IllegalArgumentException("Name is required");
        if (!Set.of("CHECKING","SAVINGS","CASH","CREDIT_CARD","BROKERAGE","OTHER").contains(a.getType())) throw new IllegalArgumentException("Invalid account type");
        if(a.getOpeningBalance()==null)a.setOpeningBalance(BigDecimal.ZERO);
        validCurrency(a.getCurrency());
        return accountView(accounts.save(a));
    }
    @PutMapping("/api/accounts/{id}") @Transactional public Object updateAccount(Authentication auth,@PathVariable UUID id,@RequestBody Account input) {
        Account a=ledger.account(id,uid(auth));
        if(input.getName()!=null) a.setName(input.getName());
        if(input.getOpeningBalance()!=null) a.setOpeningBalance(input.getOpeningBalance());
        a.setActive(input.isActive()); return accountView(accounts.save(a));
    }
    @DeleteMapping("/api/accounts/{id}") @Transactional public void archiveAccount(Authentication auth,@PathVariable UUID id) {
        Account a=ledger.account(id,uid(auth)); a.setActive(false); accounts.save(a);
    }

    @GetMapping("/api/debts") public Object debts(Authentication auth) { return debts.findByUserIdOrderByNameAsc(uid(auth)).stream().map(this::debtView).toList(); }
    @GetMapping("/api/debts/{id}") public Object debt(Authentication auth,@PathVariable UUID id) { return debtView(ledger.debt(id,uid(auth))); }
    @GetMapping("/api/debts/{id}/payments") public Object payments(Authentication auth,@PathVariable UUID id) {
        ledger.debt(id,uid(auth));
        return transactions.findByUserIdAndDebtIdOrderByTransactionDateDesc(uid(auth),id).stream()
            .map(t -> transactionService.getTransactionResponseById(t.getId().toString())).toList();
    }
    @PostMapping("/api/debts") @Transactional public Object createDebt(Authentication auth,@RequestBody Debt d) {
        d.setId(null); d.setUser(user(auth));
        if(d.getName()==null||d.getName().isBlank()||d.getDebtType()==null) throw new IllegalArgumentException("Name and debt type are required");
        positive(d.getOriginalBalance(),"Original balance"); positive(d.getOpeningBalance(),"Opening balance"); validCurrency(d.getCurrency());
        if(d.getLinkedAccount()!=null) {
            Account a=ledger.account(d.getLinkedAccount().getId(),uid(auth));
            if(!"CREDIT_CARD".equals(a.getType())||!"CREDIT_CARD".equals(d.getDebtType())||!a.getCurrency().equals(d.getCurrency())) throw new IllegalArgumentException("Linked debt must match a credit card account and currency");
            if(!a.isActive() || a.getOpeningBalance().signum() > 0 || a.getOpeningBalance().negate().compareTo(d.getOpeningBalance()) != 0) throw new IllegalArgumentException("Card opening balance must equal the negative debt opening balance");
            d.setLinkedAccount(a);
        }
        return debtView(debts.save(d));
    }
    @PutMapping("/api/debts/{id}") @Transactional public Object updateDebt(Authentication auth,@PathVariable UUID id,@RequestBody Debt input) {
        Debt d=ledger.debt(id,uid(auth)); if(input.getName()!=null)d.setName(input.getName());
        if(input.getInterestRate()!=null)d.setInterestRate(input.getInterestRate());
        if(input.getMinimumPayment()!=null)d.setMinimumPayment(input.getMinimumPayment());
        if(input.getDueDate()!=null)d.setDueDate(input.getDueDate());
        if(input.getLender()!=null)d.setLender(input.getLender());
        d.setActive(input.isActive()); return debtView(debts.save(d));
    }
    @DeleteMapping("/api/debts/{id}") @Transactional public void archiveDebt(Authentication auth,@PathVariable UUID id) { Debt d=ledger.debt(id,uid(auth));d.setActive(false);debts.save(d); }

    @GetMapping("/api/savings-goals") public Object goals(Authentication auth) { return goals.findByUserIdOrderByNameAsc(uid(auth)).stream().map(this::goalView).toList(); }
    @GetMapping("/api/savings-goals/{id}") public Object goal(Authentication auth,@PathVariable UUID id) { return goalView(ledger.goal(id,uid(auth))); }
    @PostMapping("/api/savings-goals") @Transactional public Object createGoal(Authentication auth,@RequestBody SavingsGoal g) {
        g.setId(null);g.setUser(user(auth));
        if(g.getName()==null||g.getName().isBlank())throw new IllegalArgumentException("Name is required");
        positive(g.getTargetAmount(),"Target amount");positive(g.getOpeningAmount(),"Opening amount");
        if(g.getLinkedAccount()==null||g.getLinkedAccount().getId()==null)throw new IllegalArgumentException("Savings account is required");
        Account a=ledger.account(g.getLinkedAccount().getId(),uid(auth));
        if(!a.isActive()||!"SAVINGS".equals(a.getType()))throw new IllegalArgumentException("Goal must link to an active savings account");
        g.setLinkedAccount(a); return goalView(goals.save(g));
    }
    @PutMapping("/api/savings-goals/{id}") @Transactional public Object updateGoal(Authentication auth,@PathVariable UUID id,@RequestBody SavingsGoal input) {
        SavingsGoal g=ledger.goal(id,uid(auth));if(input.getName()!=null)g.setName(input.getName());
        if(input.getTargetAmount()!=null){positive(input.getTargetAmount(),"Target amount");g.setTargetAmount(input.getTargetAmount());}
        if(input.getTargetDate()!=null)g.setTargetDate(input.getTargetDate());g.setActive(input.isActive());return goalView(goals.save(g));
    }
    @DeleteMapping("/api/savings-goals/{id}") @Transactional public void archiveGoal(Authentication auth,@PathVariable UUID id) {SavingsGoal g=ledger.goal(id,uid(auth));g.setActive(false);goals.save(g);}

    @GetMapping("/api/investment-accounts") public Object investments(Authentication auth) { return investments.findByUserIdOrderByNameAsc(uid(auth)).stream().map(this::investmentView).toList(); }
    @GetMapping("/api/investment-accounts/{id}") public Object investment(Authentication auth,@PathVariable UUID id) { return investmentView(ledger.investment(id,uid(auth))); }
    @PostMapping("/api/investment-accounts") @Transactional public Object createInvestment(Authentication auth,@RequestBody InvestmentAccount i) {
        i.setId(null);i.setUser(user(auth));
        if(i.getName()==null||i.getName().isBlank()||i.getAccountType()==null)throw new IllegalArgumentException("Name and account type are required");
        positive(i.getOpeningContributions(),"Opening contributions");
        if(i.getLinkedAccount()==null||i.getLinkedAccount().getId()==null)throw new IllegalArgumentException("Brokerage account is required");
        Account a=ledger.account(i.getLinkedAccount().getId(),uid(auth));
        if(!a.isActive()||!"BROKERAGE".equals(a.getType()))throw new IllegalArgumentException("Investment must link to an active brokerage account");
        i.setLinkedAccount(a);return investmentView(investments.save(i));
    }
    @PutMapping("/api/investment-accounts/{id}") @Transactional public Object updateInvestment(Authentication auth,@PathVariable UUID id,@RequestBody InvestmentAccount input) {
        InvestmentAccount i=ledger.investment(id,uid(auth));if(input.getName()!=null)i.setName(input.getName());
        if(input.getProvider()!=null)i.setProvider(input.getProvider());i.setActive(input.isActive());return investmentView(investments.save(i));
    }
    @DeleteMapping("/api/investment-accounts/{id}") @Transactional public void archiveInvestment(Authentication auth,@PathVariable UUID id) {InvestmentAccount i=ledger.investment(id,uid(auth));i.setActive(false);investments.save(i);}
}
