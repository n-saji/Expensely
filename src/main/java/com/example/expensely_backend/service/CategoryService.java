package com.example.expensely_backend.service;

import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final UserService userService;


    public CategoryService(CategoryRepository categoryRepository, UserService userService
                           ) {
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

    public Category getCategoryById(String id) {
        return categoryRepository.findById(UUID.fromString(id))
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
    }

//    retaining category, no need to delete
//    public void deleteCategoryById(String id) {
//        try {
//           check if category referenced by expense / budget
//            if (categoryRepository.existsById(UUID.fromString(id))) {
//                if (!expenseService.getExpensesByCategoryId(id).isEmpty()) {
//                    throw new IllegalArgumentException("Cannot delete category, it is referenced by existing expenses");
//                }
//                if (!budgetService.getBudgetByCategoryId(id).isEmpty()) {
//                    throw new IllegalArgumentException("Cannot delete category, it is referenced by existing budgets");
//                }
//            }
//            categoryRepository.deleteById(UUID.fromString(id));
//        } catch (Exception e) {
//            throw new IllegalArgumentException("Error deleting category: " + e.getMessage());
//        }
//    }

    public Iterable<Category> getAllCategories() {
        var categories = categoryRepository.findAll();
        if (!categories.iterator().hasNext()) {
            throw new IllegalArgumentException("No categories found");
        }
        return categories;
    }

    public Iterable<Category> getCategoriesByUserId(String userId, String type) {
        var user = userService.GetUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }
        List<Category> categories ;
        if (type == null || type.isEmpty()) {
            categories = categoryRepository.findByUserId(user.getId());
        } else {
            categories = categoryRepository.findByUserAndType(user, type);
        }
        categories.forEach((category1) -> {
            category1.setUser(null);
        });
        return categories;
    }


}
