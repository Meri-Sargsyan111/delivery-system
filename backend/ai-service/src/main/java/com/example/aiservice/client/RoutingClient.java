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

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RoutingClient(RestTemplate restTemplate, @Value("${routing.routing-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public RouteResult route(Coordinate from, Coordinate to) {
        String url = String.format(Locale.ROOT, "%s/%f,%f;%f,%f?overview=false",
                baseUrl, from.lon(), from.lat(), to.lon(), to.lat());

        try {
            OsrmRouteResponse response = restTemplate.getForObject(url, OsrmRouteResponse.class);
            List<OsrmRoute> routes = response == null ? null : response.routes();
            if (routes == null || routes.isEmpty()) {
                throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Route calculation is temporarily unavailable");
            }
            OsrmRoute route = routes.get(0);
            return new RouteResult(route.distance() / 1000.0, route.duration() / 60.0);
        } catch (HttpStatusCodeException | ResourceAccessException ex) {
            log.error("Routing failed from {} to {}", from, to, ex);
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Route calculation is temporarily unavailable");
        }
    }
}