package com.example.expensely_backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ExpenselyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenselyBackendApplication.class, args);
    }

}
