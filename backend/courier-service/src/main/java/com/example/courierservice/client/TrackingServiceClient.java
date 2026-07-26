package com.example.courierservice.client;

import com.example.courierservice.dto.RouteView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches the real driving-route polyline for an order from tracking-service, once, at
 * the start of live tracking (see LocationSimulatorServiceImpl.startTracking) - the
 * simulator then walks that polyline locally rather than requesting a route on every
 * tick. tracking-service requires an authenticated caller for every endpoint (see its
 * SecurityConfig); this call runs inside the same authenticated request that triggered
 * startDelivery (a courier or admin action), so the caller's own JWT is forwarded rather
 * than introducing a separate service-to-service credential.
 */
@Slf4j
@Component
public class TrackingServiceClient {

    private final RestTemplate restTemplate;
    private final String trackingServiceBaseUrl;

    public TrackingServiceClient(RestTemplate restTemplate,
                                  @Value("${services.tracking-service.base-url}") String trackingServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.trackingServiceBaseUrl = trackingServiceBaseUrl;
    }

    /**
     * Returns null (never throws) on any failure - a missing route must not prevent
     * startDelivery from succeeding; the caller falls back to skipping simulation for
     * this order rather than failing the whole request.
     */
    public RouteView getRoute(Long orderId) {
        String url = trackingServiceBaseUrl + "/tracking/" + orderId + "/route";
        try {
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders()), RouteView.class).getBody();
        } catch (HttpStatusCodeException ex) {
            log.warn("tracking-service returned an error while fetching route for order {}: {}",
                    orderId, ex.getStatusCode());
            return null;
        } catch (ResourceAccessException ex) {
            log.error("tracking-service unreachable while fetching route for order {}", orderId, ex);
            return null;
        }
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            headers.setBearerAuth(jwt.getTokenValue());
        }
        return headers;
    }
}
