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


    public void save(User user){

        if (user.getEmail() == null || user.getPhone() == null || user.getPassword() == null) {
            throw new IllegalArgumentException("Email, phone, and password must be provided");
        }
        if (user.getCurrency() == null) {
            throw new IllegalArgumentException("Currency must be provided");
        }
        try {
            Optional<User> existingUser = userRepository.findUserByEmailOrPhone(user.getEmail(), user.getPhone());
            if (existingUser.isPresent()) {
                throw new IllegalArgumentException("Email or phone already exists");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error checking email or phone: " + e.getMessage());
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        try {
            userRepository.save(user);
        }catch (Exception e){
            throw new IllegalArgumentException("Error saving user: " + e.getMessage());
        }
    }

    public Boolean authenticate(String email, String phone,String password) {


    User user = GetUserByEmailOrPhone(email, phone);
        return passwordEncoder.matches(password, user.getPassword());

    }

    public User GetUserById(String id) {
        UUID uuid = UUID.fromString(id);
        return userRepository.findById(uuid).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public User GetUserByEmailOrPhone(String email, String phone) {
        Optional<User> userOpt = userRepository.findUserByEmailOrPhone(email, phone);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        User user = userOpt.get();
        if (user.getEmail() == null || user.getPhone() == null) {
            throw new IllegalArgumentException("User email or phone is null");
        }
        return user;

    }


}
