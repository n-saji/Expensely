package com.example.expensely_backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Category category;

    @Column(nullable = false)
    private BigDecimal amount;

    private String description;

    @Column(name = "expense_date")
    private LocalDate expenseDate;
}
