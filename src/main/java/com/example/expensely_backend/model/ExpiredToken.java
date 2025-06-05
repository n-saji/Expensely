package com.example.expensely_backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "expired_tokens")
public class ExpiredToken {
    @Id
    @Getter @Setter
    private String token;

    @ManyToOne
    @Getter @Setter
    private User user;
}
