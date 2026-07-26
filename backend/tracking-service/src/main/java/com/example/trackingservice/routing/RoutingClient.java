package com.example.trackingservice.routing;

import com.example.trackingservice.entity.RouteCache;
import com.example.trackingservice.repository.RouteCacheRepository;
import com.example.trackingservice.routing.dto.Coordinate;
import com.example.trackingservice.routing.dto.OsrmRoute;
import com.example.trackingservice.routing.dto.OsrmRouteResponse;
import com.example.trackingservice.routing.dto.RouteResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Real driving route via OSRM, same base HTTP pattern as ai-service's RoutingClient but
 * requesting full geometry (ai-service's copy uses overview=false since it only needs
 * distance/duration for a price estimate; tracking-service needs the actual polyline to
 * walk/draw). Backed by a persistent RouteCache (see routing.cache-ttl-hours) so the same
 * pickup/destination pair isn't re-requested from OSRM on every order.
 */
@Slf4j
@Component
public class RoutingClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] RETRY_BACKOFF_MS = {500, 1500};
    private static final int CACHE_KEY_PRECISION = 4;

    private final RestTemplate restTemplate;
    private final RouteCacheRepository routeCacheRepository;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final int cacheTtlHours;

    public RoutingClient(RestTemplate restTemplate,
                          RouteCacheRepository routeCacheRepository,
                          ObjectMapper objectMapper,
                          @Value("${routing.routing-base-url}") String baseUrl,
                          @Value("${routing.cache-ttl-hours:24}") int cacheTtlHours) {
        this.restTemplate = restTemplate;
        this.routeCacheRepository = routeCacheRepository;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.cacheTtlHours = cacheTtlHours;
    }

    /**
     * Never throws for a background/best-effort caller - returns null on failure (cache
     * miss + OSRM unavailable after retries) so the caller can degrade gracefully.
     */
    public RouteResult route(Coordinate from, Coordinate to) {
        double fromLat = round(from.lat());
        double fromLng = round(from.lon());
        double toLat = round(to.lat());
        double toLng = round(to.lon());

        RouteCache cached = routeCacheRepository
                .findFirstByFromLatAndFromLngAndToLatAndToLngAndCreatedAtAfter(
                        fromLat, fromLng, toLat, toLng, LocalDateTime.now().minusHours(cacheTtlHours))
                .orElse(null);
        if (cached != null) {
            return toRouteResult(cached);
        }

        return routeAndCache(from, to, fromLat, fromLng, toLat, toLng);
    }

    private RouteResult routeAndCache(Coordinate from, Coordinate to,
                                       double fromLat, double fromLng, double toLat, double toLng) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                OsrmRoute route = callOsrm(from, to);
                List<double[]> geometry = route.geometry().coordinates().stream()
                        .map(point -> new double[]{point.get(1), point.get(0)})
                        .toList();
                double distanceKm = route.distance() / 1000.0;
                double durationMinutes = route.duration() / 60.0;

                saveCache(fromLat, fromLng, toLat, toLng, distanceKm, durationMinutes, geometry);
                log.info("Routed [{},{}] -> [{},{}]: {}km, {}min, {} points",
                        from.lat(), from.lon(), to.lat(), to.lon(), distanceKm, durationMinutes, geometry.size());
                return new RouteResult(distanceKm, durationMinutes, geometry);
            } catch (HttpStatusCodeException | ResourceAccessException ex) {
                log.warn("Routing attempt {}/{} failed from {} to {}: {}", attempt, MAX_ATTEMPTS, from, to, ex.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(RETRY_BACKOFF_MS[attempt - 1]);
                }
            }
        }
        log.error("Routing exhausted all retries from {} to {}", from, to);
        return null;
    }

    private OsrmRoute callOsrm(Coordinate from, Coordinate to) {
        String url = String.format(Locale.ROOT, "%s/%f,%f;%f,%f?overview=full&geometries=geojson",
                baseUrl, from.lon(), from.lat(), to.lon(), to.lat());

        OsrmRouteResponse response = restTemplate.getForObject(url, OsrmRouteResponse.class);
        List<OsrmRoute> routes = response == null ? null : response.routes();
        if (routes == null || routes.isEmpty()) {
            throw new ResourceAccessException("OSRM returned no routes from " + from + " to " + to);
        }
        return routes.get(0);
    }

    private void saveCache(double fromLat, double fromLng, double toLat, double toLng,
                            double distanceKm, double durationMinutes, List<double[]> geometry) {
        try {
            String geometryJson = objectMapper.writeValueAsString(geometry);
            routeCacheRepository.save(new RouteCache(null, fromLat, fromLng, toLat, toLng,
                    distanceKm, durationMinutes, geometryJson, LocalDateTime.now()));
        } catch (Exception e) {
            log.warn("Failed to persist route cache entry (route still returned to caller)", e);
        }
    }

    private RouteResult toRouteResult(RouteCache cached) {
        try {
            double[][] points = objectMapper.readValue(cached.getGeometryJson(), double[][].class);
            return new RouteResult(cached.getDistanceKm(), cached.getDurationMinutes(), List.of(points));
        } catch (Exception e) {
            log.warn("Failed to parse cached route geometry, treating as cache miss", e);
            return null;
        }
    }

    private double round(double value) {
        double scale = Math.pow(10, CACHE_KEY_PRECISION);
        return Math.round(value * scale) / scale;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
