package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "expenses",
        indexes = {
                @Index(name = "idx_expense_date_user_id", columnList = "user_id,expense_date")
        }
)
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Getter
    @Setter
    private UUID id;

    @ManyToOne
    @Getter
    @Setter
    private User user;

    @ManyToOne
    @Getter
    @Setter
    private Category category;

    @Column(nullable = false)
    @Getter
    @Setter
    private BigDecimal amount;

    @Getter
    @Setter
    private String description;

    @Column(name = "expense_date")
    @Getter
    @Setter
    private LocalDateTime expenseDate;

}
