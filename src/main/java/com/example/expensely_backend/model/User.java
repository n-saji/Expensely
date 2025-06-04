package com.example.expensely_backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String name;

    @Column(nullable = false)
    private String country_code;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

}
