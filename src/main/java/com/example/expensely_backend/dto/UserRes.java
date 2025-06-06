package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.User;
import lombok.Getter;

public class UserRes {

    @Getter
    private User user;
    @Getter
    private String error;

    public UserRes(User user, String error) {
        this.user = user;
        this.error = error;
    }
}
