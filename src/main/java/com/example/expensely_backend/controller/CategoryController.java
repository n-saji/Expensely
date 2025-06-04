package com.example.expensely_backend.controller;

import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
    private final CategoryService categoryService;
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/create")
    public String createCategory(@RequestBody Category category) {
        if (category.getName() == null || category.getName().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Category name must not be null or empty").getBody();
        }
        if (category.getUser() == null || category.getUser().getId() == null) {
            return ResponseEntity.badRequest().body("Error: User ID must not be null").getBody();
        }

        try {
            categoryService.save(category);
            return ResponseEntity.ok("Category created successfully!").getBody();
        }catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage()).getBody();
        }

    }
}
