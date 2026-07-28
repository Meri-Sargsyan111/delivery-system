package com.example.courierservice.client;

import com.example.courierservice.dto.RouteView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Fetches the real driving-route polyline for an order from tracking-service, at the
 * start of live tracking and on any retry of that (see
 * LocationSimulatorServiceImpl.startTracking) - the simulator then walks that polyline
 * locally rather than requesting a route on every tick. tracking-service requires an
 * authenticated caller for every endpoint (see its SecurityConfig); the bearer token is
 * passed in explicitly rather than read from SecurityContextHolder here, because a
 * retry of this call runs on a scheduled background thread (see
 * LocationSimulatorServiceImpl), which has no request-bound SecurityContext at all -
 * reading it there would silently send an unauthenticated request and get a 401
 * instead of the intended retry.
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
     * startDelivery from succeeding; the caller is responsible for deciding whether/how
     * to retry (see LocationSimulatorServiceImpl.startTracking).
     */
    public RouteView getRoute(Long orderId, String bearerToken) {
        String url = trackingServiceBaseUrl + "/tracking/" + orderId + "/route";
        HttpHeaders headers = new HttpHeaders();
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        try {
            return restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), RouteView.class).getBody();
        } catch (HttpStatusCodeException ex) {
            log.warn("tracking-service returned an error while fetching route for order {}: {}",
                    orderId, ex.getStatusCode());
            return null;
        } catch (ResourceAccessException ex) {
            log.error("tracking-service unreachable while fetching route for order {}", orderId, ex);
            return null;
        }
    }
}
