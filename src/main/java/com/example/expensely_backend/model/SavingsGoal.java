package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "savings_goals") @Getter @Setter
public class SavingsGoal {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false) private User user;
    @Column(nullable = false) private String name;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal targetAmount;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal openingAmount = BigDecimal.ZERO;
    private LocalDate targetDate;
    @ManyToOne(optional = false) private Account linkedAccount;
    @Column(nullable = false) private boolean active = true;
}
