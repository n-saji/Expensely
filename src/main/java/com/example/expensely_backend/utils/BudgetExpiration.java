package com.example.expensely_backend.utils;

import com.example.expensely_backend.dto.MessageDTO;
import com.example.expensely_backend.globals.globals;
import com.example.expensely_backend.handler.AlertHandler;
import com.example.expensely_backend.repository.BudgetRepository;
import com.example.expensely_backend.service.DbLogService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.env.Environment;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;

@Component
public class BudgetExpiration {
	private final BudgetRepository budgetRepository;
	private final Mailgun mailgun;
	private final AlertHandler alertHandler;
	private final DbLogService dbLogService;
	private final Environment environment;

	public BudgetExpiration(BudgetRepository budgetRepository, Mailgun mailgun, AlertHandler alertHandler,
	                        DbLogService dbLogService, Environment environment) {
		this.budgetRepository = budgetRepository;
		this.mailgun = mailgun;
		this.alertHandler = alertHandler;
		this.dbLogService = dbLogService;
		this.environment = environment;
	}

	@Scheduled(cron = "0 0 0 * * *")
	@Transactional
	public void checkBudgetExpiry() {
		LocalDate today = LocalDate.now();
		HashMap<String, List<String>> expiredBudgetsByUser = new HashMap<>();

		// Find budgets where endDate < today and not already expired
		var budgetsToExpire = budgetRepository.findBudgetByEndDateBeforeAndIsActiveTrue(today);

		for (var budget : budgetsToExpire) {
			budget.setActive(false);
			if (Boolean.TRUE.equals(budget.getUser().getEmailNotificationsEnabled())) {
				String userMail = budget.getUser().getEmail();
				expiredBudgetsByUser.putIfAbsent(userMail, new java.util.ArrayList<>());
				expiredBudgetsByUser.get(userMail).add(budget.getCategory().getName());
			}

			if (Boolean.TRUE.equals(budget.getUser().getInAppNotificationsEnabled())) {
				alertHandler.sendAlert(budget.getUser().getId(), MessageDTO.builder().message("Your budget " +
						"for category " + budget.getCategory().getName() + " has expired.").type(globals.MessageType.ALERT).sender(globals.SERVER_SENDER).build());
			}
		}

		for (var entry : expiredBudgetsByUser.entrySet()) {
			String userMail = entry.getKey();
			List<String> categories = entry.getValue();
			String categoriesList = String.join(", ", categories);
			String subject = "Your budgets have expired";
			String budgetUrl = environment.getProperty("FRONTEND_URL", "https://expensely.store").replaceAll("/+$", "") + "/budget";
			String text = "The following budgets have expired: " + categoriesList + ". Review your budgets: " + budgetUrl;
			mailgun.sendHtmlMessage(userMail, subject, buildExpiredBudgetEmail(categories, budgetUrl), text);
		}

		budgetRepository.saveAll(budgetsToExpire);
		dbLogService.logMessage("utils", getClass().getName(), "checkBudgetExpiry",
				"Budget expiry job ran at " + today + ", expired " + budgetsToExpire.size() + " budgets.");
	}

	static String buildExpiredBudgetEmail(List<String> categories, String budgetUrl) {
		StringBuilder items = new StringBuilder();
		for (String category : categories) {
			items.append("<tr><td style=\"padding:12px 16px;border-bottom:1px solid #e2e8f0;color:#334155;font-size:14px;\">• ")
				.append(escapeHtml(category)).append("</td></tr>");
		}
		String safeUrl = escapeHtml(budgetUrl);
		String logoUrl = escapeHtml(budgetUrl.replaceFirst("/budget$", "/expensely-logo.png"));
		return """
			<!DOCTYPE html>
			<html lang="en"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"></head>
			<body style="margin:0;padding:24px 12px;background:#f1f5f9;font-family:Arial,Helvetica,sans-serif;color:#0f172a;">
			  <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="max-width:600px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:16px;">
			    <tr><td style="padding:28px 32px;background:#0f766e;border-radius:16px 16px 0 0;text-align:center;">
			      <img src="%s" alt="Expensely logo" width="52" height="52" style="display:block;margin:0 auto 10px;border-radius:12px;">
			      <div style="font-size:24px;font-weight:700;color:#ffffff;">Expensely</div>
			    </td></tr>
			    <tr><td style="padding:32px;">
			      <div style="font-size:12px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#0d9488;">Budget update</div>
			      <h1 style="margin:12px 0 12px;font-size:26px;line-height:1.2;">Your budgets have expired</h1>
			      <p style="font-size:15px;line-height:1.6;color:#475569;">The following category budgets have reached their end date. Review them to keep your spending plan up to date.</p>
			      <table role="presentation" cellpadding="0" cellspacing="0" width="100%%" style="margin:24px 0;border:1px solid #e2e8f0;border-radius:10px;background:#f8fafc;">%s</table>
			      <table role="presentation" cellpadding="0" cellspacing="0" style="margin:28px 0 12px;"><tr><td bgcolor="#0f766e" style="border-radius:8px;">
			        <a href="%s" style="display:inline-block;padding:14px 24px;color:#ffffff;text-decoration:none;font-size:15px;font-weight:700;">Review budgets</a>
			      </td></tr></table>
			      <p style="font-size:12px;line-height:1.5;color:#64748b;">If the button does not work, open <a href="%s" style="color:#0f766e;word-break:break-all;">%s</a>.</p>
			    </td></tr>
			    <tr><td style="padding:18px 32px;border-top:1px solid #e2e8f0;text-align:center;font-size:12px;color:#94a3b8;">Expensely · Your finances, at a glance</td></tr>
			  </table>
			</body></html>
			""".formatted(logoUrl, items, safeUrl, safeUrl, safeUrl);
	}

	private static String escapeHtml(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
			.replace("\"", "&quot;").replace("'", "&#39;");
	}
}
