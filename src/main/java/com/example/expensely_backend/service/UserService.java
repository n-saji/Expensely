package com.example.expensely_backend.service;

import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }


    public User save(User user){
        user.setId(UUID.randomUUID());
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userRepository.save(user);
    }

    public Boolean authenticate(String email, String phone,String password) {

        Optional<User> userOpt = userRepository.findUserByEmailOrPhone(email, phone);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();

        return passwordEncoder.matches(password, user.getPassword());

    }


}
