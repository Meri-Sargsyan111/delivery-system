package com.example.authservice.service.impl;

import com.example.authservice.dto.AuthResponse;
import com.example.authservice.dto.LoginRequest;
import com.example.authservice.dto.RegisterRequest;
import com.example.authservice.dto.UpdateProfileRequest;
import com.example.authservice.dto.UserProfileResponse;
import com.example.authservice.dto.UserResponse;
import com.example.authservice.entity.Role;
import com.example.authservice.entity.User;
import com.example.authservice.event.CourierRegisteredEvent;
import com.example.authservice.exception.EmailAlreadyExistsException;
import com.example.authservice.exception.InvalidCredentialsException;
import com.example.authservice.exception.InvalidRegistrationRoleException;
import com.example.authservice.exception.PhoneAlreadyExistsException;
import com.example.authservice.mapper.UserMapper;
import com.example.authservice.repository.UserRepository;
import com.example.authservice.security.JwtService;
import com.example.authservice.security.UserPrincipal;
import com.example.authservice.service.AuthService;
import com.example.authservice.service.AvatarStorageService;
import com.example.authservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /**
     * The only roles a public registration request may ever produce. Deliberately an
     * explicit allowlist keyed by the client-facing string (not a blind uppercase-and-
     * prepend-"ROLE_" scheme): that would let "ADMIN" resolve to Role.ROLE_ADMIN, which
     * must never be reachable from this endpoint.
     */
    private static final Map<String, Role> PUBLICLY_REGISTRABLE_ROLES = Map.of(
            "CUSTOMER", Role.ROLE_CUSTOMER,
            "COURIER", Role.ROLE_COURIER
    );

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final AvatarStorageService avatarStorageService;
    private final EmailService emailService;
    private final KafkaTemplate<String, CourierRegisteredEvent> courierRegisteredKafkaTemplate;

    @Override
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(
                    "An account with email " + request.getEmail() + " already exists");
        }
        if (userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new PhoneAlreadyExistsException(
                    "An account with phone number " + request.getPhoneNumber() + " already exists");
        }

        Role role = resolvePubliclyRegistrableRole(request.getRole());

        User user = new User();
        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setEmail(request.getEmail());
        user.setPhoneNumber(request.getPhoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setRole(role);

        User saved = userRepository.save(user);

        if (role == Role.ROLE_COURIER) {
            courierRegisteredKafkaTemplate.send("courier-registered",
                    new CourierRegisteredEvent(saved.getId(), saved.getFirstName(), saved.getLastName()));
        }

        emailService.sendWelcomeEmail(saved.getEmail(), saved.getFirstName());

        return userMapper.toResponse(saved);
    }

    /**
     * @throws InvalidRegistrationRoleException for null, blank, unrecognized, or
     *         disallowed values (including "ADMIN"/"ROLE_ADMIN") - never silently
     *         defaults, so a caller can't accidentally end up with the wrong role.
     */
    private Role resolvePubliclyRegistrableRole(String rawRole) {
        if (rawRole == null || rawRole.isBlank()) {
            throw new InvalidRegistrationRoleException(
                    "role is required and must be one of: CUSTOMER, COURIER");
        }

        Role role = PUBLICLY_REGISTRABLE_ROLES.get(rawRole.trim().toUpperCase());
        if (role == null) {
            throw new InvalidRegistrationRoleException(
                    "Unsupported role '" + rawRole + "' - must be one of: CUSTOMER, COURIER");
        }
        return role;
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        } catch (AuthenticationException ex) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        User user = ((UserPrincipal) authentication.getPrincipal()).getUser();
        return buildAuthResponse(user);
    }

    @Override
    public AuthResponse issueAccessToken(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Account no longer exists"));
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        return new AuthResponse(
                accessToken, "Bearer", jwtService.getAccessTokenTtlSeconds(), userMapper.toResponse(user));
    }

    @Override
    public UserProfileResponse getCurrentUser() {
        User user = loadCurrentUser();
        return userMapper.toProfileResponse(user);
    }

    @Override
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = loadCurrentUser();

        if (!user.getPhoneNumber().equals(request.getPhoneNumber())
                && userRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new PhoneAlreadyExistsException(
                    "An account with phone number " + request.getPhoneNumber() + " already exists");
        }

        user.setFirstName(request.getFirstName());
        user.setLastName(request.getLastName());
        user.setPhoneNumber(request.getPhoneNumber());

        User saved = userRepository.save(user);
        return userMapper.toProfileResponse(saved);
    }

    /**
     * Stores the new file and persists its URL before deleting the old one - if the save
     * ever failed, the previous avatar would still be intact rather than losing both.
     */
    @Override
    public UserProfileResponse uploadAvatar(MultipartFile file) {
        User user = loadCurrentUser();

        String oldAvatarUrl = user.getAvatarUrl();
        String newAvatarUrl = avatarStorageService.store(file);
        user.setAvatarUrl(newAvatarUrl);

        User saved = userRepository.save(user);
        avatarStorageService.delete(oldAvatarUrl);

        return userMapper.toProfileResponse(saved);
    }

    private User loadCurrentUser() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UUID userId = UUID.fromString(jwt.getSubject());
        return userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Account no longer exists"));
    }
}
