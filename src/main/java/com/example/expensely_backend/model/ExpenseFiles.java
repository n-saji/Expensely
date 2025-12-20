package com.example.expensely_backend.model;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

import java.util.UUID;

@Entity
@Table(name = "expense_files")
public class ExpenseFiles {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Getter
    @Setter
    private UUID id;

    @Getter
    @Setter
    @Column(nullable = false)
    private String userId;

    @Getter
    @Setter
    @Column(nullable = false)
    private String fileName;

    @Getter
    @Setter
    @Column(nullable = false)
    private String fileType;

    @Getter
    @Setter
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String expenses;

    @Column(nullable = false)
    @Getter
    @Setter
    private Long createdAt;

    @Column(nullable = false)
    @Getter
    @Setter
    private Long expiresAt;


}
