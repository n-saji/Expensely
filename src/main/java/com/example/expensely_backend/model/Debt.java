package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity @Table(name = "debts") @Getter @Setter
public class Debt {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false) private User user;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 32) private String debtType;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal originalBalance;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal openingBalance;
    @Column(precision = 8, scale = 4) private BigDecimal interestRate;
    @Column(precision = 19, scale = 2) private BigDecimal minimumPayment;
    private LocalDate dueDate;
    private String lender;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false) private boolean active = true;
    @OneToOne private Account linkedAccount;
}
