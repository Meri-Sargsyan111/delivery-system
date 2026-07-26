package com.example.authservice.service;

import com.example.authservice.dto.AuthResponse;
import com.example.authservice.dto.CustomerSummaryResponse;
import com.example.authservice.dto.DeleteAccountRequest;
import com.example.authservice.dto.LoginRequest;
import com.example.authservice.dto.RegisterRequest;
import com.example.authservice.dto.UpdatePreferencesRequest;
import com.example.authservice.dto.UpdateProfileRequest;
import com.example.authservice.dto.UserContactResponse;
import com.example.authservice.dto.UserPreferencesResponse;
import com.example.authservice.dto.UserProfileResponse;
import com.example.authservice.dto.UserResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

    List<CustomerSummaryResponse> listCustomers();

    CustomerSummaryResponse getCustomerById(UUID id);

    /**
     * Cross-service contact lookup, any role, unlike {@link #getCustomerById} which is
     * customer-only - see PublicUserController. Excludes a disabled (e.g. deleted, see
     * danger-zone delete-account) account, same as a not-found id, so a stale
     * courierId/customerId never surfaces a deleted user's placeholder data as if real.
     *
     * @throws com.example.authservice.exception.UserNotFoundException if no enabled user exists with this id
     */
    UserContactResponse getUserContact(UUID id);

    /**
     * Batch form of {@link #getUserContact} - resolves as many of the given ids as exist
     * and are enabled; unknown/disabled ids are simply absent from the result rather than
     * throwing, since a partial roster is the expected/normal case (a courier row with no
     * linked account, in particular).
     */
    List<UserContactResponse> getUserContacts(List<UUID> ids);

    UserPreferencesResponse getPreferences();

    /**
     * @throws com.example.authservice.exception.InvalidPreferenceException if theme/language
     *         is non-null but not one of the supported values
     */
    UserPreferencesResponse updatePreferences(UpdatePreferencesRequest request);

    /**
     * Deletes (anonymizes) the current user's account: personal data (name, email, phone,
     * avatar, password) is scrubbed and the account is disabled, but the row itself is kept
     * so historical delivery/order/payment/chat records that reference this user id remain
     * intact. Does not revoke refresh tokens itself - the caller is responsible for that
     * (see RefreshTokenService#revokeAllForUser), the same way login/logout orchestrate
     * the refresh cookie alongside this service.
     *
     * The password field is optional (the current frontend relies on the Bearer token alone
     * as proof of identity and sends no body at all - see AuthController#deleteAccount,
     * which substitutes an empty request when none is sent). When a password IS supplied,
     * it must match, so a stricter future client can still require re-authentication.
     *
     * @return the deleted user's id
     * @throws com.example.authservice.exception.InvalidCredentialsException if a password
     *         was supplied and it does not match the account's current password
     */
    UUID deleteAccount(DeleteAccountRequest request);
}
