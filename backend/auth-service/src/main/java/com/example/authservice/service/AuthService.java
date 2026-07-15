package com.example.authservice.service;

import com.example.authservice.dto.AuthResponse;
import com.example.authservice.dto.LoginRequest;
import com.example.authservice.dto.RegisterRequest;
import com.example.authservice.dto.UpdateProfileRequest;
import com.example.authservice.dto.UserProfileResponse;
import com.example.authservice.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    /**
     * Builds a fresh access token (and accompanying user info) for an already-verified
     * user, without re-checking credentials. Used by {@code /auth/refresh} once the
     * presented refresh token itself has been validated.
     */
    AuthResponse issueAccessToken(UUID userId);

    UserProfileResponse getCurrentUser();

    UserProfileResponse updateProfile(UpdateProfileRequest request);

    UserProfileResponse uploadAvatar(MultipartFile file);
}
