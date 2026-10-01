package com.example.expensely_backend.utils;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BudgetExpirationEmailTest {
    @Test
    void rendersLogoBudgetLinkAndEscapedCategories() {
        String html = BudgetExpiration.buildExpiredBudgetEmail(
                List.of("Food & Drinks", "<script>alert('x')</script>"),
                "https://expensely.store/budget");

        assertTrue(html.contains("https://expensely.store/expensely-logo.png"));
        assertTrue(html.contains("href=\"https://expensely.store/budget\""));
        assertTrue(html.contains("Food &amp; Drinks"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertFalse(html.contains("<script>"));
    }
}
