package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.AlertDtos;
import com.example.expensely_backend.model.Budget;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ExpiredTokenRepository expiredTokenRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;


    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder, ExpiredTokenRepository expiredTokenRepository,
                       BudgetRepository budgetRepository, CategoryRepository categoryRepository, ExpenseRepository expenseRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.expiredTokenRepository = expiredTokenRepository;
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.expenseRepository = expenseRepository;
    }


    public void save(User user) {

        if (user.getEmail() == null) {
            throw new IllegalArgumentException("Email must be provided");
        }
        if ((user.getPhone() == null || user.getPhone().isEmpty()) && !user.isOauth2User()) {
            throw new IllegalArgumentException("Phone must be provided");
        }
        if ((user.getPassword() == null || user.getPassword().isEmpty()) && !user.isOauth2User()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        Optional<User> existingUser = userRepository.findUserByEmailOrPhone(user.getEmail(), user.getPhone());
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException("Email or phone already exists");
        }

        if (user.getPassword() != null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
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

    public User GetActiveUserById(String id) {
        UUID uuid = UUID.fromString(id);
        User user = userRepository.findById(uuid).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (user.getIsActive() == null || !user.getIsActive()) {
            throw new IllegalArgumentException("User is not active");
        }
        return user;
    }

    public User GetUserById(String id) {
        UUID uuid = UUID.fromString(id);
        return userRepository.findById(uuid).orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    public User GetUserByEmailOrPhone(String email, String phone) {
        Optional<User> userOpt = userRepository.findUserByEmailOrPhone(email, phone);
        if (userOpt.isEmpty()) {
            throw new IllegalArgumentException("account not found");
        }

        User user = userOpt.get();
        if (user.getEmail() == null) {
            throw new IllegalArgumentException("User email  is null");
        }
        if (user.getPhone() == null && !user.isOauth2User()) {
            throw new IllegalArgumentException("User phone is null");
        }

        return user;
    }

    public boolean isUserPresent(String email, String phone) {
        Optional<User> userOpt = userRepository.findUserByEmailOrPhone(email, phone);
        return userOpt.isPresent();
    }

    public User UpdateUser(User user) {
        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error updating user: " + e.getMessage());
        }
        return user;
    }

    public User updatePassword(User user) {
        String password = user.getPassword();
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        user.setPassword(passwordEncoder.encode(password));
        try {
            userRepository.save(user);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error updating password: " + e.getMessage());
        }
        return user;
    }

    public List<User> getAllUsers() {
        List<User> users = userRepository.findAllOrderByCreatedAtDesc();
        for (User user : users) {
            user.setPassword(null);
        }
        if (users.isEmpty()) {
            throw new IllegalArgumentException("No users found");
        }
        return users;
    }

    @Transactional
    public void deleteUser(User user) {
        try {
            expiredTokenRepository.deleteAllByUserId(user.getId());
            budgetRepository.deleteAllByUserId(user.getId());
            expenseRepository.deleteAllByUserId(user.getId());
            categoryRepository.deleteByUserId(user.getId());
            userRepository.delete(user);
        } catch (Exception e) {
            throw new IllegalArgumentException("Error deleting user: " + e.getMessage());
        }
    }

    public List<AlertDtos> fetchAllAlertsForUser(String userId) {
        Optional<User> user = userRepository.findById(UUID.fromString(userId));
        if (user.isEmpty()) {
            throw new IllegalArgumentException("User not found");
        }

        List<AlertDtos> alerts = new ArrayList<>();

        List<Budget> budgets = budgetRepository.findByUserId(user.get().getId());
        if (budgets.isEmpty()) {
            return alerts;
        }


        for (Budget budget : budgets) {
            float ratio =
                    ((budget.getAmountSpent())).divide(budget.getAmountLimit(), RoundingMode.CEILING).floatValue();
            if (ratio >= 0.70 && ratio <= 1) {
                AlertDtos alert =
                        new AlertDtos("You are about to exceed limit set in " + budget.getCategory().getName()
                                , "WARNING");
                alerts.add(alert);
            } else if (ratio > 1) {
                AlertDtos alert =
                        new AlertDtos("You have exceeded limit set in " + budget.getCategory().getName()
                                , "CRITICAL");
            }
        }

        return alerts;

    }


}
