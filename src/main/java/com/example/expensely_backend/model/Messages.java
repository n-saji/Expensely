package com.example.expensely_backend.model;

import com.example.expensely_backend.globals.globals;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class Messages {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private UUID userId;
    private String message;
    private globals.MessageType type;
    private boolean isDelivered = false;
    private boolean isSeen = false;
    private LocalDateTime createdAt = LocalDateTime.now();


}
