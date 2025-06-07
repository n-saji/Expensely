package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.Category;
import com.example.expensely_backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    List<Category> findByUserAndType(User user, String type);

    
}
