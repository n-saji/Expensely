package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage()).getBody();
        }

    }

    @GetMapping("/{id}")
    public ResponseEntity<Category> getCategoryById(@PathVariable String id) {
        try {
            Category category = categoryService.getCategoryById(id);
            return ResponseEntity.ok(category);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

//    Not required for now, as categories are not deleted directly
//    @DeleteMapping("/{id}")
//    public ResponseEntity<String> deleteCategoryById(@PathVariable String id) {
//        try {
//            categoryService.deleteCategoryById(id);
//            return ResponseEntity.ok("Category deleted successfully!");
//        } catch (Exception e) {
//            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
//        }
//    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getCategoriesByUserId(@PathVariable String userId,
                                                   @RequestParam(required = false) String type) {
        try {
            return ResponseEntity.ok(categoryService.getCategoriesByUserId(userId, type));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @PatchMapping("/update/{id}")
    public ResponseEntity<?> updateCategory(@PathVariable String id, @RequestBody Category category) {
        try {
            categoryService.updateCategory(id, category);
            return ResponseEntity.ok(new AuthResponse("Category updated successfully!", null, null));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new AuthResponse("Error updating category", null, e.getMessage()));
        }
    }

}
