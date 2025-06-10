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


    public void save(User user) {

        if (user.getEmail() == null || user.getPhone() == null || user.getPassword() == null) {
            throw new IllegalArgumentException("Email, phone, and password must be provided");
        }
        try {
            Optional<User> existingUser = userRepository.findUserByEmailOrPhone(user.getEmail(), user.getPhone());
            if (existingUser.isPresent() && existingUser.get().getId() != user.getId()) {
                throw new IllegalArgumentException("Email or phone already exists");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Error checking email or phone: " + e.getMessage());
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error saving user: " + e.getMessage());
        }
    }

    public Boolean authenticate(String email, String phone, String password) {


        User user = GetUserByEmailOrPhone(email, phone);
        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new IllegalArgumentException("User is not active");
        }
        return passwordEncoder.matches(password, user.getPassword());

    }

    public User GetUserById(String id) {
        UUID uuid = UUID.fromString(id);
        User user =  userRepository.findById(uuid).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new IllegalArgumentException("User is not active");
        }
        return user;
    }

    public User GetUserByEmailOrPhone(String email, String phone) {
        Optional<User> userOpt = userRepository.findUserByEmailOrPhone(email, phone);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("account not found");
        }

        User user = userOpt.get();
        if (user.getEmail() == null || user.getPhone() == null) {
            throw new IllegalArgumentException("User email or phone is null");
        }
        return user;
    }

    public User UpdateUser(User user) {
        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error updating user: " + e.getMessage());
        }
        return user;
    }


}
