package com.example.expensely_backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "users")
public class User {

    @Setter
    @Getter
    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Getter
    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Getter
    private String name;

    @Getter
    @Column(nullable = false)
    private String country_code;

    @Getter
    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    @Getter
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Getter
    private String currency = "USD"; // Default currency set to USD

    @Column(nullable = false, columnDefinition = "varchar(255) default 'light'")
    @Getter
    @Setter
    private String theme = "light"; // Default theme set to light

    @Column(nullable = false, columnDefinition = "varchar(255) default 'en'")
    @Getter
    @Setter
    private String language = "en"; // Default language set to English

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Getter
    @Setter
    private Boolean isActive = true; // Default active status set to true

    @Column(nullable = false, columnDefinition = "boolean default false")
    @Getter
    @Setter
    private Boolean isAdmin = false;

    @Column(nullable = false, columnDefinition = "boolean default true")
    @Getter
    @Setter
    private Boolean NotificationsEnabled = true; // Default notifications enabled status set to true

}
