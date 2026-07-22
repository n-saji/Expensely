package com.example.expensely_backend.repository;

import com.example.expensely_backend.model.UserOAuthAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserOAuthAccountRepository extends JpaRepository<UserOAuthAccount, UUID> {

    List<UserOAuthAccount> findByUserId(UUID userId);

    Optional<UserOAuthAccount> findByUserIdAndProvider(UUID userId, String provider);

    Optional<UserOAuthAccount> findByProviderAndProviderUserId(String provider, String providerUserId);

    Optional<UserOAuthAccount> findByProviderAndProviderEmail(String provider, String providerEmail);

    @Transactional
    void deleteByUserIdAndProvider(UUID userId, String provider);

    long countByUserId(UUID userId);
}
