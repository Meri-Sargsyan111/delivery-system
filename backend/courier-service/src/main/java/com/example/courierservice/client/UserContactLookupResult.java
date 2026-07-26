package com.example.courierservice.client;

import java.util.UUID;

/** Mirrors auth-service's UserContactResponse (GET /users/{id}/contact) field-for-field. */
public record UserContactLookupResult(UUID id, String fullName, String phoneNumber) {
}