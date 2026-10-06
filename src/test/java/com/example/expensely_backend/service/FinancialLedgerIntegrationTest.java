package com.example.expensely_backend.service;

import com.example.expensely_backend.config.TestS3Config;
import com.example.expensely_backend.model.*;
import com.example.expensely_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test") @Import(TestS3Config.class) @Transactional
class FinancialLedgerIntegrationTest {
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired AccountRepository accounts;
    @Autowired DebtRepository debts;
    @Autowired SavingsGoalRepository goals;
    @Autowired InvestmentAccountRepository investments;
    @Autowired TransactionRepository transactions;
    @Autowired TransactionService transactionService;
    @Autowired FinancialTransactionService movements;
    @Autowired FinancialLedgerService ledger;
    @MockitoBean BudgetService budgets;
    private User user;
    private Account checking;
    private Category food;

    private BigDecimal money(String value) { return new BigDecimal(value); }
    @BeforeEach void setup() {
        user=new User();user.setEmail(UUID.randomUUID()+"@example.com");user.setName("Ledger Test");user.setPassword("unused");user.setCurrency("USD");user=users.saveAndFlush(user);
        checking=new Account();checking.setUser(user);checking.setName("Checking");checking.setType("CHECKING");checking.setCurrency("USD");checking.setOpeningBalance(money("2000"));checking=accounts.saveAndFlush(checking);
        food=new Category();food.setUser(user);food.setName("Food");food.setType("expense");food=categories.saveAndFlush(food);
    }
    private Transaction normal(TransactionType type,String amount,Category category) {
        Transaction t=new Transaction();t.setUser(user);t.setType(type);t.setAmount(money(amount));t.setCurrency("USD");t.setCategory(category);t.setAccount(checking);t.setDescription(type.name());t.setTransactionDate(LocalDateTime.now());
        return transactionService.save(t);
    }
    private Transaction movement(TransactionType type,String amount,Account source) {
        Transaction t=new Transaction();t.setType(type);t.setAmount(money(amount));t.setAccount(source);t.setTransactionDate(LocalDateTime.now());return t;
    }
    @Test void incomeAndExpenseAffectOnlyTheirAccountAndReports() {
        normal(TransactionType.EXPENSE,"100",food);
        transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("1900")));
        Category salary=new Category();salary.setUser(user);salary.setName("Salary");salary.setType("income");salary=categories.saveAndFlush(salary);
        normal(TransactionType.INCOME,"1000",salary);
        transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("2900")));
        var summary=ledger.summary(user.getId(),LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1));
        assertEquals(0,summary.get("income").compareTo(money("1000")));
        assertEquals(0,summary.get("expense").compareTo(money("100")));
    }
    @Test void debtPaymentAndInterestReverseOnEditAndDelete() {
        checking.setOpeningBalance(money("10000"));accounts.saveAndFlush(checking);
        Debt debt=new Debt();debt.setUser(user);debt.setName("Loan");debt.setDebtType("STUDENT_LOAN");debt.setCurrency("USD");debt.setOriginalBalance(money("30000"));debt.setOpeningBalance(money("30000"));debt=debts.saveAndFlush(debt);
        Transaction request=movement(TransactionType.DEBT_PAYMENT,"5000",checking);request.setDebt(debt);request.setPrincipalAmount(money("4600"));request.setInterestAmount(money("400"));
        Transaction saved=movements.create(user.getId(),request);transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("5000")));
        assertEquals(0,ledger.debtBalance(debt).compareTo(money("25400")));
        var summary=ledger.summary(user.getId(),LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1));
        assertEquals(0,summary.get("expense").compareTo(money("400")));
        Transaction edited=movement(TransactionType.DEBT_PAYMENT,"4000",checking);edited.setDebt(debt);edited.setPrincipalAmount(money("3600"));edited.setInterestAmount(money("400"));
        movements.update(user.getId(),saved.getId(),edited);transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("6000")));
        assertEquals(0,ledger.debtBalance(debt).compareTo(money("26400")));
        movements.delete(user.getId(),saved.getId());transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("10000")));
        assertEquals(0,ledger.debtBalance(debt).compareTo(money("30000")));
    }
    @Test void savingsInvestmentAndOrdinaryTransferDoNotInflateExpense() {
        checking.setOpeningBalance(money("5000"));accounts.saveAndFlush(checking);
        Account savings=new Account();savings.setUser(user);savings.setName("Savings");savings.setType("SAVINGS");savings.setCurrency("USD");savings.setOpeningBalance(money("1000"));savings=accounts.saveAndFlush(savings);
        SavingsGoal goal=new SavingsGoal();goal.setUser(user);goal.setName("Emergency");goal.setTargetAmount(money("10000"));goal.setOpeningAmount(money("1000"));goal.setLinkedAccount(savings);goal=goals.saveAndFlush(goal);
        Transaction save=movement(TransactionType.SAVINGS_TRANSFER,"500",checking);save.setSavingsGoal(goal);movements.create(user.getId(),save);
        Account brokerage=new Account();brokerage.setUser(user);brokerage.setName("Brokerage");brokerage.setType("BROKERAGE");brokerage.setCurrency("USD");brokerage.setOpeningBalance(money("2000"));brokerage=accounts.saveAndFlush(brokerage);
        InvestmentAccount investment=new InvestmentAccount();investment.setUser(user);investment.setName("Broker");investment.setAccountType("BROKERAGE");investment.setOpeningContributions(money("2000"));investment.setLinkedAccount(brokerage);investment=investments.saveAndFlush(investment);
        Transaction invest=movement(TransactionType.INVESTMENT_TRANSFER,"500",checking);invest.setInvestmentAccount(investment);movements.create(user.getId(),invest);
        Transaction transfer=movement(TransactionType.TRANSFER,"500",checking);transfer.setDestinationAccount(savings);movements.create(user.getId(),transfer);transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("3500")));
        assertEquals(0,ledger.accountBalance(savings).compareTo(money("2000")));
        assertEquals(0,ledger.goalBalance(goal).compareTo(money("1500")));
        assertEquals(0,ledger.investmentContributions(investment).compareTo(money("2500")));
        assertEquals(0,ledger.summary(user.getId(),LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1)).get("expense").compareTo(BigDecimal.ZERO));
    }
    @Test void creditCardAndOwnershipRules() {
        Account card=new Account();card.setUser(user);card.setName("Card");card.setType("CREDIT_CARD");card.setCurrency("USD");card.setOpeningBalance(money("-1000"));card=accounts.saveAndFlush(card);
        Debt debt=new Debt();debt.setUser(user);debt.setName("Card debt");debt.setDebtType("CREDIT_CARD");debt.setCurrency("USD");debt.setOriginalBalance(money("1000"));debt.setOpeningBalance(money("1000"));debt.setLinkedAccount(card);debt=debts.saveAndFlush(debt);
        Transaction purchase=new Transaction();purchase.setUser(user);purchase.setType(TransactionType.EXPENSE);purchase.setCategory(food);purchase.setAccount(card);purchase.setAmount(money("100"));purchase.setCurrency("USD");purchase.setDescription("Food");purchase.setTransactionDate(LocalDateTime.now());transactionService.save(purchase);
        transactions.flush();
        assertEquals(0,ledger.debtBalance(debt).compareTo(money("1100")));
        Transaction payment=movement(TransactionType.DEBT_PAYMENT,"500",checking);payment.setDebt(debt);movements.create(user.getId(),payment);transactions.flush();
        assertEquals(0,ledger.debtBalance(debt).compareTo(money("600")));
        UUID cardId=card.getId();
        assertThrows(IllegalArgumentException.class,()->ledger.account(cardId,UUID.randomUUID()));
        Transaction invalid=movement(TransactionType.TRANSFER,"10",checking);invalid.setDebt(debt);invalid.setDestinationAccount(card);
        assertThrows(IllegalArgumentException.class,()->movements.create(user.getId(),invalid));
    }
    @Test void unassignedLegacyRecordStaysOutOfAccountBalance() {
        Transaction legacy=new Transaction();legacy.setUser(user);legacy.setType(TransactionType.EXPENSE);
        legacy.setCategory(food);legacy.setAmount(money("25"));legacy.setCurrency("USD");
        legacy.setDescription("Historical expense");legacy.setTransactionDate(LocalDateTime.now());
        transactionService.save(legacy);transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("2000")));
        assertEquals(0,ledger.summary(user.getId(),LocalDateTime.now().minusDays(1),LocalDateTime.now().plusDays(1)).get("expense").compareTo(money("25")));
    }
    @Test void transferEditAndBulkDeleteReverseBothAccounts() {
        Account savings=new Account();savings.setUser(user);savings.setName("Savings");savings.setType("SAVINGS");
        savings.setCurrency("USD");savings.setOpeningBalance(money("100"));savings=accounts.saveAndFlush(savings);
        Transaction input=movement(TransactionType.TRANSFER,"200",checking);input.setDestinationAccount(savings);
        Transaction saved=movements.create(user.getId(),input);transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("1800")));
        assertEquals(0,ledger.accountBalance(savings).compareTo(money("300")));
        Transaction edited=movement(TransactionType.TRANSFER,"50",savings);edited.setDestinationAccount(checking);
        movements.update(user.getId(),saved.getId(),edited);transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("2050")));
        assertEquals(0,ledger.accountBalance(savings).compareTo(money("50")));
        movements.bulkDelete(user.getId(),java.util.List.of(saved));transactions.flush();
        assertEquals(0,ledger.accountBalance(checking).compareTo(money("2000")));
        assertEquals(0,ledger.accountBalance(savings).compareTo(money("100")));
    }
}
