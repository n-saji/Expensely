package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "categories")
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Getter @Setter
    private UUID id;

    @ManyToOne
    @Setter @Getter
    private User user;

    @Column(nullable = false)
@Getter @Setter
    private String name;

    @Column(nullable = false)
@Getter @Setter
    private String type; // "expense" or "income"
}
