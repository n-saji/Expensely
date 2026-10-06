package com.example.expensely_backend.controller;

import com.example.expensely_backend.dto.AuthResponse;
import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {
	private final CategoryService categoryService;

	public CategoryController(CategoryService categoryService) {
		this.categoryService = categoryService;
	}

	@PostMapping("/create")
	public String createCategory(Authentication authentication,@RequestBody Category category) {
		if (category.getName() == null || category.getName().isEmpty()) {
			return ResponseEntity.badRequest().body("Error: Category name must not be null or empty").getBody();
		}
		if(authentication==null)return "Unauthorized";
		var owner=new com.example.expensely_backend.model.User();
		owner.setId(java.util.UUID.fromString((String)authentication.getPrincipal()));
		category.setUser(owner);

		try {
			categoryService.save(category);
			return ResponseEntity.ok("Category created successfully!").getBody();
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Error: " + e.getMessage()).getBody();
		}

	}

	@GetMapping("/{id}")
	public ResponseEntity<Category> getCategoryById(Authentication authentication,@PathVariable String id) {
		try {
			Category category = categoryService.getCategoryById(id);
			if(authentication==null||category.getUser()==null||!category.getUser().getId().toString().equals(authentication.getPrincipal()))return ResponseEntity.status(403).build();
			return ResponseEntity.ok(category);
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(null);
		}
	}

	//    Not required for now, as categories are not deleted directly
	@DeleteMapping("/{id}")
	public ResponseEntity<String> deleteCategoryById(Authentication authentication,
	                                                 @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		try {
			categoryService.deleteCategoryById(id, userId);
			return ResponseEntity.ok("Category deleted successfully!");
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@GetMapping("/user")
	public ResponseEntity<?> getCategoriesByUserId(Authentication authentication,

	                                               @RequestParam(required = false) String type) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(403).body("Error: You are not authorized to access categories for this user");
		}
		try {
			return ResponseEntity.ok(categoryService.getCategoriesByUserId(userId, type));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

	@PatchMapping("/update/{id}")
	public ResponseEntity<?> updateCategory(Authentication authentication,@PathVariable String id, @RequestBody Category category) {
		try {
			Category existing=categoryService.getCategoryById(id);
			if(authentication==null||existing.getUser()==null||!existing.getUser().getId().toString().equals(authentication.getPrincipal()))return ResponseEntity.status(403).build();
			categoryService.updateCategory(id, category);
			return ResponseEntity.ok(new AuthResponse("Category updated successfully!", null, null));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(new AuthResponse("Error updating category", null, e.getMessage()));
		}
	}

	@GetMapping("/find-category-dependencies/{id}")
	public ResponseEntity<?> getCategoryDependencies(Authentication authentication, @PathVariable String id) {
		String userId = (String) authentication.getPrincipal();
		if (userId == null) {
			return ResponseEntity.status(403).body("Error: You are not authorized to access category dependencies for this user");
		}
		try {
			return ResponseEntity.ok(categoryService.getCategoryDependenciesForUser(userId, id));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Error: " + e.getMessage());
		}
	}

}
