package com.example.expensely_backend.service;

import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final UserService userService;
    public CategoryService(CategoryRepository categoryRepository, UserService userService) {
        this.categoryRepository = categoryRepository;
        this.userService = userService;
    }

    public Category save(Category category) {
        if (category.getUser() == null || category.getUser().getId() == null) {
            throw new IllegalArgumentException("User must be provided");
        }
        var user = userService.GetUserById(category.getUser().getId().toString());
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        category.setUser(user);
        return categoryRepository.save(category);
    }
}
