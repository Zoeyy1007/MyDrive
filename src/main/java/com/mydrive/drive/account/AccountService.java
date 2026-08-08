
package com.mydrive.drive.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.mydrive.drive.account.dto.RegisterRequest;
import com.mydrive.drive.account.dto.UserResponse;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@Service
public class AccountService {
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(AppUserRepository appUserRepository, PasswordEncoder passwordEncoder) {
        this.appUserRepository = appUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (appUserRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(email);
        }
        String passwordHash = passwordEncoder.encode(request.password());
        var user = new AppUser(UUID.randomUUID(), email, passwordHash, Instant.now());
        var savedUser = appUserRepository.save(user);
        return toResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public AppUser requireByEmail(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        return appUserRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new AppUserNotFoundException(normalizedEmail));
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}
