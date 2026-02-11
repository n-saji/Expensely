package com.example.expensely_backend.utils;

import com.example.expensely_backend.dto.AuthResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<?> handleNotFound(NoHandlerFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                        "status", 404,
                        "message", "Endpoint not found"
                ));
    }
    
    // Handles malformed JSON or invalid request body
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity
                .badRequest()
                .body(new AuthResponse("Invalid request body or wrong data format", null, ex.getMessage()));
    }

    // Optional: handle validation errors
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        return ResponseEntity
                .badRequest()
                .body(new AuthResponse("Validation failed", null, ex.getMessage()));
    }

    @ExceptionHandler(NullPointerException.class)
    public ResponseEntity<?> handleNullPointerExceptions(NullPointerException ex) {
        return ResponseEntity
                .badRequest()
                .body(new AuthResponse("Null pointer exception", null, ex.getMessage()));
    }
}
