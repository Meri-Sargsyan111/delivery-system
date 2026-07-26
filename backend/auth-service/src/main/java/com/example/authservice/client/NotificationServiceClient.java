package com.example.authservice.client;

import com.example.authservice.exception.NotificationServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Aggregation client: the frontend's Settings page calls one combined endpoint,
 * GET/PUT /auth/me/preferences (theme + language + notification toggles in a single
 * object - see AuthServiceImpl), but the notification toggles are stored in
 * notification-service, not here. This forwards the caller's own bearer token
 * (notification-service's /notifications/preferences resolves the user from it, same as
 * every other per-user endpoint there) rather than a service-to-service credential, since
 * the call is always made on behalf of the currently authenticated user.
 */
@Slf4j
@Component
public class NotificationServiceClient {

    private final RestTemplate restTemplate;
    private final String notificationServiceBaseUrl;

    public NotificationServiceClient(
            RestTemplate restTemplate,
            @Value("${services.notification-service.base-url}") String notificationServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.notificationServiceBaseUrl = notificationServiceBaseUrl;
    }

    /**
     * Degrades to safe (all-enabled) defaults rather than failing the whole preferences
     * page just because notification-service hiccupped for a read.
     */
    public NotificationPreferencesLookupResult getPreferences(String bearerToken) {
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders(bearerToken));
            ResponseEntity<NotificationPreferencesLookupResult> response = restTemplate.exchange(
                    notificationServiceBaseUrl + "/notifications/preferences",
                    HttpMethod.GET, entity, NotificationPreferencesLookupResult.class);
            return response.getBody() != null ? response.getBody() : NotificationPreferencesLookupResult.defaults();
        } catch (HttpStatusCodeException | ResourceAccessException ex) {
            log.warn("notification-service unreachable while fetching preferences, returning defaults", ex);
            return NotificationPreferencesLookupResult.defaults();
        }
    }

    /**
     * An explicit user action (Save) - unlike the GET above, a failure here is surfaced
     * rather than silently swallowed, so the caller knows the toggle didn't actually persist.
     */
    public NotificationPreferencesLookupResult updatePreferences(
            String bearerToken, NotificationPreferencesUpdateRequest update) {
        try {
            HttpEntity<NotificationPreferencesUpdateRequest> entity =
                    new HttpEntity<>(update, authHeaders(bearerToken));
            ResponseEntity<NotificationPreferencesLookupResult> response = restTemplate.exchange(
                    notificationServiceBaseUrl + "/notifications/preferences",
                    HttpMethod.PUT, entity, NotificationPreferencesLookupResult.class);
            if (response.getBody() == null) {
                throw new NotificationServiceUnavailableException("notification-service returned an empty response");
            }
            return response.getBody();
        } catch (HttpStatusCodeException | ResourceAccessException ex) {
            log.error("notification-service call failed while updating preferences", ex);
            throw new NotificationServiceUnavailableException(
                    "Notification preferences are temporarily unavailable, please try again");
        }
    }

    private HttpHeaders authHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(bearerToken);
        return headers;
    }
}
