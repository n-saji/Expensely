package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "expenses")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @Getter @Setter
    private User user;

    @ManyToOne
    @Getter @Setter
    private Category category;

    @Column(nullable = false)
    @Getter
    private BigDecimal amount;

    @Getter
    private String description;

    @Column(name = "expense_date")
    private LocalDateTime expenseDate = LocalDateTime.now();
}
