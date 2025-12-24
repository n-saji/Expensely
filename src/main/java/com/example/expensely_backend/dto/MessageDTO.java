package com.example.expensely_backend.dto;

import com.example.expensely_backend.globals.globals;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageDTO {
    private String id;
    private String message;
    private String sender;
    private LocalDateTime time = LocalDateTime.now();
    private globals.MessageType type;
    private Boolean isRead = false;


}
