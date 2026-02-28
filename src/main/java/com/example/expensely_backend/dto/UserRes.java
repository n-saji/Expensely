package com.example.expensely_backend.dto;

import com.example.expensely_backend.model.User;

public record UserRes(User user, String error) {

}
