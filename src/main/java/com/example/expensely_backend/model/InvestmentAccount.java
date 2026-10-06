package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "investment_accounts") @Getter @Setter
public class InvestmentAccount {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false) private User user;
    @Column(nullable = false) private String name;
    private String provider;
    @Column(nullable = false, length = 32) private String accountType;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal openingContributions = BigDecimal.ZERO;
    @OneToOne(optional = false) private Account linkedAccount;
    @Column(nullable = false) private boolean active = true;
}
