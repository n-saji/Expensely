package com.example.expensely_backend.utils;

import com.example.expensely_backend.repository.BudgetRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
public class BudgetExpiration {
    private final BudgetRepository budgetRepository;

    public BudgetExpiration(BudgetRepository budgetRepository) {
        this.budgetRepository = budgetRepository;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkBudgetExpiry() {
        LocalDate today = LocalDate.now();

        // Find budgets where endDate < today and not already expired
        var budgetsToExpire = budgetRepository.findByEndDateBeforeAndIsActiveTrue(today);

        for (var budget : budgetsToExpire) {
            budget.setActive(false);
        }

        budgetRepository.saveAll(budgetsToExpire);
        System.out.println("Budget expiry job ran at " + today + ", expired " + budgetsToExpire.size() + " budgets.");
    }
}
