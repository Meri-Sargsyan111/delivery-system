package com.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body is optional (the frontend currently sends none, relying on the Bearer token alone
 * as proof of identity - see AuthController#deleteAccount). When a password IS supplied,
 * it is validated and a mismatch rejects the deletion - see AuthServiceImpl#deleteAccount.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteAccountRequest {

    private String password;
}