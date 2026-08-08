
package com.mydrive.drive.security;

import com.mydrive.drive.account.AppUser;
import com.mydrive.drive.account.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import java.util.Locale;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserRepository appUserRepository;

    public AppUserDetailsService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String email = username.trim().toLowerCase(java.util.Locale.ROOT);
        AppUser appUser = appUserRepository.findByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
        return User.withUsername(appUser.getEmail())
                .password(appUser.getPasswordHash())
                .roles("USER")
                .build();
    }
}