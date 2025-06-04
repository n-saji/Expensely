package com.example.expensely_backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurring_expenses")
public class RecurringExpense {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Category category;

    private BigDecimal amount;

    private String description;

    private String recurrence; // "daily", "weekly", "monthly"

    private LocalDate nextOccurrence;

    private boolean active;
}
