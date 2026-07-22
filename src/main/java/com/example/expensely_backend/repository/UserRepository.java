package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.User;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	Optional<User> findByPhone(String phone);

	Optional<User> findUserByEmailOrPhone(String email, String phone);

	@Query("SELECT u FROM User u ORDER BY u.createdAt DESC")
	List<User> findAllOrderByCreatedAtDesc();

	@Modifying
	@Transactional
	@Query(value = "UPDATE users SET theme_color = 'teal' WHERE theme_color IS NULL", nativeQuery = true)
	int backfillThemeColorDefaults();

	@Modifying
	@Transactional
	@Query(value = "UPDATE users SET has_transactions = true WHERE id = :userId AND (has_transactions IS FALSE OR has_transactions IS NULL)", nativeQuery = true)
	int markHasTransactions(@Param("userId") UUID userId);
}
