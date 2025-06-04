package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(
        name = "budgets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category_id", "month", "year"})
)
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne
    @Setter @Getter
    private User user;
    @ManyToOne
    @Setter @Getter
    private Category category;
    @Getter
    private BigDecimal amountLimit;
    @Getter
    private int month; // 1 - 12
    @Getter
    private int year;


}
