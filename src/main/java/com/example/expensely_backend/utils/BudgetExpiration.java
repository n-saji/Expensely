package com.example.expensely_backend.utils;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.handler.AlertHandler;
import com.example.expensely_backend.repository.BudgetRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

@Component
public class BudgetExpiration {
    private final BudgetRepository budgetRepository;
    private final Mailgun mailgun;
    private final AlertHandler alertHandler;

    public BudgetExpiration(BudgetRepository budgetRepository, Mailgun mailgun, AlertHandler alertHandler) {
        this.budgetRepository = budgetRepository;
        this.mailgun = mailgun;
        this.alertHandler = alertHandler;
    }

    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void checkBudgetExpiry() {
        LocalDate today = LocalDate.now();
        HashMap<String, List<String>> expiredBudgetsByUser = new HashMap<>();

        // Find budgets where endDate < today and not already expired
        var budgetsToExpire = budgetRepository.findByEndDateBeforeAndIsActiveTrue(today);

        for (var budget : budgetsToExpire) {
            budget.setActive(false);
            String userMail = budget.getUser().getEmail();
            expiredBudgetsByUser.putIfAbsent(userMail, new java.util.ArrayList<>());
            expiredBudgetsByUser.get(userMail).add(budget.getCategory().getName());

            alertHandler.sendAlert(budget.getUser().getId(), MessageDTO.builder().message("Your budget " +
                    "for category " + budget.getCategory().getName() + " has expired.").type(globals.MessageType.ALERT).sender(globals.SERVER_SENDER).build());
        }

        for (var entry : expiredBudgetsByUser.entrySet()) {
            String userMail = entry.getKey();
            List<String> categories = entry.getValue();
            String categoriesList = String.join(", ", categories);
            String subject = "Your budgets have expired";
            String text = "The following budgets have expired: " + categoriesList + ". Please review your budgets.";
            mailgun.sendSimpleMessage(userMail, subject, text);
        }

        budgetRepository.saveAll(budgetsToExpire);
        System.out.println("Budget expiry job ran at " + today + ", expired " + budgetsToExpire.size() + " budgets.");
    }
}
