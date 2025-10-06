package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "budgets",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "category_id","startDate","endDate","isActive"} )
)
public class Budget {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Getter @Setter
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne
    @Setter @Getter
    private User user;

    @ManyToOne
    @Setter @Getter
    private Category category;

    @Getter @Setter
    @Column(nullable = false,precision = 10, scale = 2)
    private BigDecimal amountLimit;

    @Getter @Setter
    @Column(precision = 10, scale = 2)
    private BigDecimal amountSpent;

    @Getter @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Period period;

    @Getter @Setter
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Getter @Setter
    @Column(name = "end_date")
    private LocalDate endDate;

    @Getter @Setter
    @Column(name = "is_active",columnDefinition = "boolean default true")
    private boolean isActive = true;

    @Getter @Setter
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Getter @Setter
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum Period {
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY,
        CUSTOM
    }

}
