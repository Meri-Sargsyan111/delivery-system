package com.example.aiservice.client;

import com.example.aiservice.client.dto.Coordinate;
import com.example.aiservice.client.dto.NominatimResult;
import com.example.aiservice.exception.AiServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Resolves a free-text address to coordinates via Nominatim (OpenStreetMap's public
 * geocoding service) - no API key required, unlike OpenRouteService's geocoder, which
 * this project has no key configured for. See application.yml's routing.* properties.
 */
@Slf4j
@Component
public class GeocodingClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public GeocodingClient(RestTemplate restTemplate, @Value("${routing.geocoding-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public Coordinate geocode(String address) {
        URI url = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .queryParam("q", address)
                .queryParam("format", "json")
                .queryParam("limit", 1)
                .build()
                .encode()
                .toUri();

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, "DeliveryOS-ai-service/1.0");

        try {
            NominatimResult[] results = restTemplate
                    .exchange(url, HttpMethod.GET, new HttpEntity<>(headers), NominatimResult[].class)
                    .getBody();

            if (results == null || results.length == 0) {
                throw new AiServiceException(HttpStatus.UNPROCESSABLE_ENTITY, "Could not resolve address: " + address);
            }
            return new Coordinate(Double.parseDouble(results[0].lat()), Double.parseDouble(results[0].lon()));
        } catch (HttpStatusCodeException | ResourceAccessException ex) {
            log.error("Geocoding failed for address '{}'", address, ex);
            throw new AiServiceException(HttpStatus.SERVICE_UNAVAILABLE, "Address lookup is temporarily unavailable");
        }
    }
}