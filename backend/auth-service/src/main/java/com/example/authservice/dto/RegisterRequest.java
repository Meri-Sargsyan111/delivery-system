package com.example.authservice.dto;

import com.example.authservice.validation.PasswordMatches;
import com.example.authservice.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@PasswordMatches
public class RegisterRequest {

    @NotBlank
    @Size(max = 100)
    private String firstName;

    @NotBlank
    @Size(max = 100)
    private String lastName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @NotBlank
    @Size(max = 20)
    private String phoneNumber;

    @StrongPassword
    private String password;

    @NotBlank
    private String confirmPassword;

    /**
     * Public self-registration role. Only "CUSTOMER" and "COURIER" are ever accepted -
     * see AuthServiceImpl.resolvePubliclyRegistrableRole(). Deliberately a plain String,
     * not the Role enum: binding straight to the enum would let a client discover valid
     * constant names (including ROLE_ADMIN) via Jackson's error messages, and would
     * require the client to send the internal "ROLE_" prefix. The explicit allowlist in
     * the service layer is the single source of truth for what's actually registrable.
     */
    @NotBlank
    private String role;
}
