package com.example.aiservice.client;

import com.example.aiservice.client.dto.Coordinate;
import com.example.aiservice.client.dto.OsrmRoute;
import com.example.aiservice.client.dto.OsrmRouteResponse;
import com.example.aiservice.client.dto.RouteResult;
import com.example.aiservice.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Locale;

/**
 * Real driving distance/duration via OSRM's public routing server - no API key
 * required, unlike OpenRouteService's directions API, which this project has no key
 * configured for. See application.yml's routing.* properties.
 */
@Slf4j
@Component
public class RoutingClient {

    /**
     * A DNS lookup or connect attempt to a public host occasionally fails transiently
     * (observed directly in this environment: router.project-osrm.org intermittently
     * throws UnknownHostException even though the same host resolves fine moments
     * later/earlier) without the host actually being down. One retry recovers from
     * that class of blip; it deliberately does NOT retry HttpStatusCodeException
     * (a real 4xx/5xx from OSRM itself won't be fixed by asking again).
     */
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 400;

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RoutingClient(RestTemplate restTemplate, @Value("${routing.routing-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public RouteResult route(Coordinate from, Coordinate to) {
        String url = String.format(Locale.ROOT, "%s/%f,%f;%f,%f?overview=false",
                baseUrl, from.lon(), from.lat(), to.lon(), to.lat());

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                OsrmRouteResponse response = restTemplate.getForObject(url, OsrmRouteResponse.class);
                List<OsrmRoute> routes = response == null ? null : response.routes();
                if (routes == null || routes.isEmpty()) {
                    throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Route calculation is temporarily unavailable");
                }
                OsrmRoute route = routes.get(0);
                return new RouteResult(route.distance() / 1000.0, route.duration() / 60.0);
            } catch (HttpStatusCodeException ex) {
                log.error("Routing failed from {} to {} (OSRM returned {})", from, to, ex.getStatusCode(), ex);
                throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Route calculation is temporarily unavailable");
            } catch (ResourceAccessException ex) {
                log.warn("Routing attempt {}/{} failed from {} to {} (transient network/DNS error): {}",
                        attempt, MAX_ATTEMPTS, from, to, ex.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    log.error("Routing failed from {} to {} after {} attempts", from, to, MAX_ATTEMPTS, ex);
                    throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Route calculation is temporarily unavailable");
                }
                sleepBeforeRetry();
            }
        }
        throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Route calculation is temporarily unavailable");
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Route calculation is temporarily unavailable");
        }
    }
}