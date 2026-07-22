package com.example.expensely_backend.service;

import com.example.expensely_backend.dto.OAuthAccountDto;
import com.example.expensely_backend.model.User;
import com.example.expensely_backend.model.UserOAuthAccount;
import com.example.expensely_backend.repository.UserOAuthAccountRepository;
import com.example.expensely_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class OAuthService {

    private final UserOAuthAccountRepository oAuthAccountRepository;
    private final UserRepository userRepository;

    public OAuthService(UserOAuthAccountRepository oAuthAccountRepository, UserRepository userRepository) {
        this.oAuthAccountRepository = oAuthAccountRepository;
        this.userRepository = userRepository;
    }

    public List<OAuthAccountDto> getLinkedAccounts(UUID userId) {
        return oAuthAccountRepository.findByUserId(userId).stream()
                .map(acc -> new OAuthAccountDto(
                        acc.getProvider().toLowerCase(),
                        acc.getProviderUserId(),
                        acc.getProviderEmail(),
                        acc.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Transactional
    public OAuthAccountDto linkAccount(UUID userId, String provider, String providerUserId, String providerEmail) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        String normProvider = provider.toLowerCase().trim();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check if providerUserId is linked to another user
        if (providerUserId != null && !providerUserId.isBlank()) {
            Optional<UserOAuthAccount> existingOther = oAuthAccountRepository.findByProviderAndProviderUserId(normProvider, providerUserId);
            if (existingOther.isPresent() && !existingOther.get().getUser().getId().equals(userId)) {
                throw new IllegalStateException("This " + normProvider + " account is already linked to another Expensely user.");
            }
        }

        // Check if user already has this provider linked
        Optional<UserOAuthAccount> existingUserLink = oAuthAccountRepository.findByUserIdAndProvider(userId, normProvider);
        UserOAuthAccount account;
        if (existingUserLink.isPresent()) {
            account = existingUserLink.get();
            if (providerUserId != null && !providerUserId.isBlank()) {
                account.setProviderUserId(providerUserId);
            }
            if (providerEmail != null && !providerEmail.isBlank()) {
                account.setProviderEmail(providerEmail);
            }
        } else {
            account = new UserOAuthAccount();
            account.setUser(user);
            account.setProvider(normProvider);
            account.setProviderUserId(providerUserId != null && !providerUserId.isBlank() ? providerUserId : (providerEmail != null ? providerEmail : userId.toString()));
            account.setProviderEmail(providerEmail != null ? providerEmail : user.getEmail());
            account.setCreatedAt(LocalDateTime.now());
        }

        user.setOauth2User(true);
        userRepository.save(user);
        UserOAuthAccount saved = oAuthAccountRepository.save(account);

        return new OAuthAccountDto(saved.getProvider(), saved.getProviderUserId(), saved.getProviderEmail(), saved.getCreatedAt());
    }

    @Transactional
    public void unlinkAccount(UUID userId, String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider is required");
        }
        String normProvider = provider.toLowerCase().trim();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<UserOAuthAccount> existing = oAuthAccountRepository.findByUserIdAndProvider(userId, normProvider);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("No linked " + normProvider + " account found.");
        }

        // Unlink safety check: If user has no password set and only 1 OAuth account linked
        long oauthCount = oAuthAccountRepository.countByUserId(userId);
        boolean hasPassword = user.getPassword() != null && !user.getPassword().isBlank();

        if (!hasPassword && oauthCount <= 1) {
            throw new IllegalStateException("Cannot unlink your only login method. Please set a password in your Security settings before unlinking.");
        }

        oAuthAccountRepository.deleteByUserIdAndProvider(userId, normProvider);
    }

    @Transactional
    public User processOAuthLogin(String provider, String providerUserId, String email, String name) {
        String normProvider = (provider != null ? provider : "google").toLowerCase().trim();

        // 1. Try finding by provider and providerUserId
        if (providerUserId != null && !providerUserId.isBlank()) {
            Optional<UserOAuthAccount> oAuthAccountOpt = oAuthAccountRepository.findByProviderAndProviderUserId(normProvider, providerUserId);
            if (oAuthAccountOpt.isPresent()) {
                return oAuthAccountOpt.get().getUser();
            }
        }

        // 2. Try finding by email
        if (email != null && !email.isBlank()) {
            Optional<User> userOpt = userRepository.findByEmail(email);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (!user.isEmailVerified()) {
                    user.setEmailVerified(true);
                }
                user.setOauth2User(true);
                userRepository.save(user);

                // Ensure link is saved
                linkAccount(user.getId(), normProvider, providerUserId, email);
                return user;
            }
        }

        // 3. Register new user
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required for OAuth login");
        }

        User newUser = new User();
        newUser.setEmail(email);
        newUser.setName(name != null && !name.isBlank() ? name : email.split("@")[0]);
        newUser.setOauth2User(true);
        newUser.setEmailVerified(true);
        newUser.setProfileComplete(false);
        newUser.setCreatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(newUser);
        linkAccount(savedUser.getId(), normProvider, providerUserId, email);

        return savedUser;
    }
}
