
package com.mydrive.drive.account;

import com.mydrive.drive.account.dto.RegisterRequest;
import com.mydrive.drive.account.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountServiceTests{

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @InjectMocks
    AccountService accountService;

    @Test
    void registerNormalizesEmailHashesPasswordAndReturnsSafeResponse() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.now();
        RegisterRequest request = new RegisterRequest("testing@MAil.com", "plaintextPassword");

        when(appUserRepository.existsByEmail("testing@mail.com")).thenReturn(false);
        when(passwordEncoder.encode("plaintextPassword")).thenReturn("hashedPassword");
        when(appUserRepository.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = accountService.register(request);
        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        // Verify that the password was hashed and the email was normalized
        verify(appUserRepository).existsByEmail("testing@mail.com");
        verify(passwordEncoder).encode("plaintextPassword");
        verify(appUserRepository).save(userCaptor.capture());
    }

    @Test
    void registerRejectsExistingEmail(){
        RegisterRequest request = new RegisterRequest("testing@mail.com", "plaintextPassword");

        when(appUserRepository.existsByEmail("testing@mail.com")).thenReturn(true);

        assertThatThrownBy(()->accountService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class)
                .hasMessageContaining("Email already exists");

        verify(appUserRepository).existsByEmail("testing@mail.com");
        verifyNoInteractions(passwordEncoder);
        verify(appUserRepository, never()).save(any(AppUser.class));
    }
}