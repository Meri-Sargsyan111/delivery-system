package com.example.trackingservice.routing;

import com.example.trackingservice.entity.GeocodeCache;
import com.example.trackingservice.repository.GeocodeCacheRepository;
import com.example.trackingservice.routing.dto.Coordinate;
import com.example.trackingservice.routing.dto.NominatimResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Resolves a free-text address to coordinates via Nominatim, same HTTP pattern as
 * ai-service's GeocodingClient (URI pre-encoding, required User-Agent - see that class
 * for why), but with two additions ai-service's copy doesn't need: a persistent cache
 * (GeocodeCache - tracking-service calls this far more often, once per new order plus
 * retries, not once per user-initiated estimate) and a minimum-interval throttle to stay
 * within Nominatim's ~1 req/sec usage policy, which nothing in this codebase enforced
 * before this.
 */
@Slf4j
@Component
public class GeocodingClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final long[] RETRY_BACKOFF_MS = {500, 1500};

    private final RestTemplate restTemplate;
    private final GeocodeCacheRepository geocodeCacheRepository;
    private final String baseUrl;
    private final long minRequestIntervalMs;

    private final Object throttleLock = new Object();
    private long lastRequestAtMs = 0;

    public GeocodingClient(RestTemplate restTemplate,
                            GeocodeCacheRepository geocodeCacheRepository,
                            @Value("${routing.geocoding-base-url}") String baseUrl,
                            @Value("${routing.min-request-interval-ms:1100}") long minRequestIntervalMs) {
        this.restTemplate = restTemplate;
        this.geocodeCacheRepository = geocodeCacheRepository;
        this.baseUrl = baseUrl;
        this.minRequestIntervalMs = minRequestIntervalMs;
    }

    /**
     * Never throws for a background/best-effort caller - returns null on failure so the
     * caller can degrade gracefully (skip this tick, retry later). Interactive REST
     * callers that need a hard failure should catch a null result themselves and map it
     * to GeocodingUnavailableException at that layer.
     */
    public Coordinate geocode(String address) {
        String normalized = normalize(address);

        return geocodeCacheRepository.findByNormalizedAddress(normalized)
                .map(cache -> new Coordinate(cache.getLatitude(), cache.getLongitude()))
                .orElseGet(() -> geocodeAndCache(address, normalized));
    }

    private Coordinate geocodeAndCache(String address, String normalized) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            throttle();
            try {
                Coordinate coordinate = callNominatim(address);
                geocodeCacheRepository.save(new GeocodeCache(
                        null, normalized, coordinate.lat(), coordinate.lon(), LocalDateTime.now()));
                log.info("Geocoded '{}' -> [{}, {}]", address, coordinate.lat(), coordinate.lon());
                return coordinate;
            } catch (HttpStatusCodeException | ResourceAccessException ex) {
                log.warn("Geocoding attempt {}/{} failed for address '{}': {}", attempt, MAX_ATTEMPTS, address, ex.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(RETRY_BACKOFF_MS[attempt - 1]);
                }
            }
        }
        log.error("Geocoding exhausted all retries for address '{}'", address);
        return null;
    }

    private Coordinate callNominatim(String address) {
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("q", address)
                .queryParam("format", "json")
                .queryParam("limit", 1)
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "DeliveryOS-tracking-service/1.0");

        NominatimResult[] results = restTemplate
                .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), NominatimResult[].class)
                .getBody();

        if (results == null || results.length == 0) {
            throw new ResourceAccessException("Nominatim returned no results for: " + address);
        }
        return new Coordinate(Double.parseDouble(results[0].lat()), Double.parseDouble(results[0].lon()));
    }

    private String normalize(String address) {
        return address.trim().toLowerCase(Locale.ROOT);
    }

    private void throttle() {
        synchronized (throttleLock) {
            long waitMs = minRequestIntervalMs - (System.currentTimeMillis() - lastRequestAtMs);
            if (waitMs > 0) {
                sleep(waitMs);
            }
            lastRequestAtMs = System.currentTimeMillis();
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
