package com.example.authservice.bootstrap;

import com.example.authservice.entity.Role;
import com.example.authservice.entity.User;
import com.example.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Admins cannot self-register, so something has to create the first one.
 * Creates a default admin account on startup if no ROLE_ADMIN account exists yet.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.bootstrap.email}")
    private String adminEmail;

    @Value("${admin.bootstrap.password}")
    private String adminPassword;

    @Value("${admin.bootstrap.first-name}")
    private String adminFirstName;

    @Value("${admin.bootstrap.last-name}")
    private String adminLastName;

    @Value("${admin.bootstrap.phone-number}")
    private String adminPhoneNumber;

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ROLE_ADMIN)) {
            return;
        }

        User admin = new User();
        admin.setFirstName(adminFirstName);
        admin.setLastName(adminLastName);
        admin.setEmail(adminEmail);
        admin.setPhoneNumber(adminPhoneNumber);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.ROLE_ADMIN);

        userRepository.save(admin);
        log.warn("No admin account existed — created default admin '{}'. " +
                "Change this password immediately outside of local development.", adminEmail);
    }
}
