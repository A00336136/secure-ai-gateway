package com.secureai.config;

import com.secureai.model.User;
import com.secureai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Seeds test data for the 'test' profile.
 * Provides the admin user that integration tests expect to find.
 */
@Component
@Profile("test")
public class TestDataInitializer {

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @PostConstruct
    public void seed() {
        if (!userRepository.existsByUsername("admin")) {
            userRepository.save(User.builder()
                    .username("admin")
                    .password(passwordEncoder.encode("Admin@123"))
                    .email("admin@test.local")
                    .role("ADMIN")
                    .enabled(true)
                    .build());
        }
    }
}
