package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.UUID;

@Entity @Table(name = "accounts") @Getter @Setter
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(optional = false) private User user;
    @Column(nullable = false) private String name;
    @Column(nullable = false, length = 32) private String type;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal openingBalance = BigDecimal.ZERO;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false) private boolean active = true;
}
