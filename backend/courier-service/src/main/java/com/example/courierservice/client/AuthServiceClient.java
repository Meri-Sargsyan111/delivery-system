package com.example.courierservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cross-service phone lookup for the live-tracking contact card (see
 * CourierAssignmentServiceImpl.getContactCard) - auth-service is the only place a user's
 * phone number lives. Mirrors order-service's AuthServiceClient pattern.
 */
@Slf4j
@Component
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    private final String authServiceBaseUrl;

    public AuthServiceClient(RestTemplate restTemplate,
                              @Value("${services.auth-service.base-url}") String authServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.authServiceBaseUrl = authServiceBaseUrl;
    }

    /**
     * Returns null (rather than throwing) on any failure - not found, auth-service
     * unreachable, or any other error - so the contact card degrades to placeholder
     * values instead of failing the whole endpoint (see requirement: "If rating/statistics
     * are not implemented, return placeholder values without breaking the API").
     */
    public UserContactLookupResult getUserContact(UUID userId) {
        if (userId == null) {
            return null;
        }
        String url = authServiceBaseUrl + "/users/" + userId + "/contact";
        try {
            return restTemplate.getForObject(url, UserContactLookupResult.class);
        } catch (HttpStatusCodeException ex) {
            log.warn("auth-service returned {} while fetching contact info for user {}", ex.getStatusCode(), userId);
            return null;
        } catch (ResourceAccessException ex) {
            log.warn("auth-service unreachable while fetching contact info for user {}", userId, ex);
            return null;
        }
    }

    /**
     * Batch form used by the courier roster/list endpoints (see CourierAssignmentServiceImpl.
     * toResponse) so listing N couriers costs one auth-service round trip instead of N -
     * matters here since the frontend routinely requests up to 1000 at once. Returns an
     * empty map (never throws) on any failure, same degrade-to-placeholder behavior as the
     * single-user lookup above; unknown/disabled userIds are simply absent from the map.
     */
    public Map<UUID, UserContactLookupResult> getUserContacts(List<UUID> userIds) {
        List<UUID> nonNullIds = userIds.stream().filter(java.util.Objects::nonNull).toList();
        if (nonNullIds.isEmpty()) {
            return Map.of();
        }
        String url = authServiceBaseUrl + "/users/contacts/batch";
        try {
            UserContactLookupResult[] results = restTemplate.postForObject(
                    url, new BatchContactRequest(nonNullIds), UserContactLookupResult[].class);
            if (results == null) {
                return Map.of();
            }
            return java.util.Arrays.stream(results)
                    .collect(Collectors.toMap(UserContactLookupResult::id, Function.identity()));
        } catch (HttpStatusCodeException ex) {
            log.warn("auth-service returned {} while batch-fetching {} contact(s)", ex.getStatusCode(), nonNullIds.size());
            return Map.of();
        } catch (ResourceAccessException ex) {
            log.warn("auth-service unreachable while batch-fetching {} contact(s)", nonNullIds.size(), ex);
            return Map.of();
        }
    }

    private record BatchContactRequest(List<UUID> ids) {
    }
}