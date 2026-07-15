package com.example.authservice.service;

import com.example.authservice.dto.AuthResponse;
import com.example.authservice.dto.LoginRequest;
import com.example.authservice.dto.RegisterRequest;
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
import com.example.authservice.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtService jwtService;
    @Mock private UserMapper userMapper;
    @Mock private KafkaTemplate<String, CourierRegisteredEvent> courierRegisteredKafkaTemplate;

    @InjectMocks private AuthServiceImpl authService;

    @Test
    void register_customerRole_persistsAsCustomerAndReturnsResponse() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@example.com", "+1111111111", "Str0ng!Pass", "Str0ng!Pass", "CUSTOMER");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+1111111111")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UserResponse expectedResponse = new UserResponse();
        when(userMapper.toResponse(any(User.class))).thenReturn(expectedResponse);

        UserResponse response = authService.register(request);

        assertThat(response).isEqualTo(expectedResponse);
        verify(userRepository).save(argThatUserHasRole(Role.ROLE_CUSTOMER));
    }

    @Test
    void register_courierRole_persistsAsCourier() {
        RegisterRequest request = new RegisterRequest(
                "Jane", "Rider", "jane@example.com", "+1111111112", "Str0ng!Pass", "Str0ng!Pass", "COURIER");

        when(userRepository.existsByEmail("jane@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+1111111112")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse());

        authService.register(request);

        verify(userRepository).save(argThatUserHasRole(Role.ROLE_COURIER));
    }

    @Test
    void register_courierRole_publishesCourierRegisteredEvent() {
        UUID savedId = UUID.randomUUID();
        RegisterRequest request = new RegisterRequest(
                "Jane", "Rider", "jane3@example.com", "+1111111114", "Str0ng!Pass", "Str0ng!Pass", "COURIER");

        when(userRepository.existsByEmail("jane3@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+1111111114")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(savedId);
            return user;
        });
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse());

        authService.register(request);

        ArgumentCaptor<CourierRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(CourierRegisteredEvent.class);
        verify(courierRegisteredKafkaTemplate).send(eq("courier-registered"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getUserId()).isEqualTo(savedId);
        assertThat(eventCaptor.getValue().getFirstName()).isEqualTo("Jane");
        assertThat(eventCaptor.getValue().getLastName()).isEqualTo("Rider");
    }

    @Test
    void register_customerRole_doesNotPublishCourierRegisteredEvent() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john4@example.com", "+1111111115", "Str0ng!Pass", "Str0ng!Pass", "CUSTOMER");

        when(userRepository.existsByEmail("john4@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+1111111115")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse());

        authService.register(request);

        verify(courierRegisteredKafkaTemplate, never()).send(any(), any());
    }

    @Test
    void register_roleIsCaseInsensitiveAndTrimmed() {
        RegisterRequest request = new RegisterRequest(
                "Jane", "Rider", "jane2@example.com", "+1111111113", "Str0ng!Pass", "Str0ng!Pass", "  courier  ");

        when(userRepository.existsByEmail("jane2@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+1111111113")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse());

        authService.register(request);

        verify(userRepository).save(argThatUserHasRole(Role.ROLE_COURIER));
    }

    @Test
    void register_encodesPasswordBeforePersisting() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@example.com", "+1111111111", "Str0ng!Pass", "Str0ng!Pass", "CUSTOMER");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+1111111111")).thenReturn(false);
        when(passwordEncoder.encode("Str0ng!Pass")).thenReturn("hashed-value");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userMapper.toResponse(any(User.class))).thenReturn(new UserResponse());

        authService.register(request);

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                user -> "hashed-value".equals(user.getPasswordHash()) && !"Str0ng!Pass".equals(user.getPasswordHash())));
    }

    @Test
    void register_adminRole_isRejectedAndNeverPersisted() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@example.com", "+1111111111", "Str0ng!Pass", "Str0ng!Pass", "ADMIN");
        lenient().when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        lenient().when(userRepository.existsByPhoneNumber("+1111111111")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(InvalidRegistrationRoleException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_roleAlreadyPrefixed_isRejected() {

        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@example.com", "+1111111111", "Str0ng!Pass", "Str0ng!Pass", "ROLE_CUSTOMER");
        lenient().when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        lenient().when(userRepository.existsByPhoneNumber("+1111111111")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(InvalidRegistrationRoleException.class);

        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPERUSER", "root", "customer;drop table users", "ROLE_COURIER", "123"})
    void register_unknownOrMalformedRole_isRejected(String badRole) {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@example.com", "+1111111111", "Str0ng!Pass", "Str0ng!Pass", badRole);
        lenient().when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        lenient().when(userRepository.existsByPhoneNumber("+1111111111")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(InvalidRegistrationRoleException.class);

        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void register_missingOrBlankRole_isRejected(String blankRole) {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@example.com", "+1111111111", "Str0ng!Pass", "Str0ng!Pass", blankRole);
        lenient().when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        lenient().when(userRepository.existsByPhoneNumber("+1111111111")).thenReturn(false);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(InvalidRegistrationRoleException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicateEmail_throwsEmailAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@example.com", "+1111111111", "Str0ng!Pass", "Str0ng!Pass", "CUSTOMER");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_duplicatePhone_throwsPhoneAlreadyExistsException() {
        RegisterRequest request = new RegisterRequest(
                "John", "Doe", "john@example.com", "+1111111111", "Str0ng!Pass", "Str0ng!Pass", "CUSTOMER");

        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(userRepository.existsByPhoneNumber("+1111111111")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(PhoneAlreadyExistsException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void login_validCredentials_returnsAuthResponseWithAccessToken() {
        LoginRequest request = new LoginRequest("john@example.com", "Str0ng!Pass");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("john@example.com");
        user.setRole(Role.ROLE_CUSTOMER);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(user), null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateAccessToken(user)).thenReturn("signed-jwt");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        UserResponse expectedResponse = new UserResponse();
        when(userMapper.toResponse(user)).thenReturn(expectedResponse);

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("signed-jwt");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900L);
        assertThat(response.getUser()).isEqualTo(expectedResponse);
    }

    @Test
    void login_afterCourierRegistration_generatesAccessTokenForCourierUser() {

        LoginRequest request = new LoginRequest("jane@example.com", "Str0ng!Pass");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("jane@example.com");
        user.setRole(Role.ROLE_COURIER);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new UserPrincipal(user), null);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(jwtService.generateAccessToken(user)).thenReturn("courier-signed-jwt");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(userMapper.toResponse(user)).thenReturn(new UserResponse());

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("courier-signed-jwt");
        verify(jwtService).generateAccessToken(argThatUserHasRole(Role.ROLE_COURIER));
    }

    @Test
    void login_invalidCredentials_throwsInvalidCredentialsException() {
        LoginRequest request = new LoginRequest("john@example.com", "wrong-password");
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad creds"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtService, never()).generateAccessToken(any());
    }

    private User argThatUserHasRole(Role role) {
        return org.mockito.ArgumentMatchers.argThat(user -> user.getRole() == role);
    }
}
