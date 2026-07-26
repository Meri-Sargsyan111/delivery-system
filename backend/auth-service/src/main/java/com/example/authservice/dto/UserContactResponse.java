package com.example.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Minimal cross-service contact projection - deliberately excludes email/role/everything
 * else on User. Used by courier-service's live-tracking contact card (see
 * PublicUserController) so a customer/courier viewing an order can see the other party's
 * name/phone without either service needing to join against the other's full user record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserContactResponse {

    private UUID id;
    private String fullName;
    private String phoneNumber;
}
