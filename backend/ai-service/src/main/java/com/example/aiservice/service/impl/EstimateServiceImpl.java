package com.example.aiservice.service.impl;

import com.example.aiservice.client.GeocodingClient;
import com.example.aiservice.client.OllamaClient;
import com.example.aiservice.client.RoutingClient;
import com.example.aiservice.client.dto.Coordinate;
import com.example.aiservice.client.dto.RouteResult;
import com.example.aiservice.dto.EstimateClaimResponse;
import com.example.aiservice.dto.EstimateRequest;
import com.example.aiservice.dto.EstimateResponse;
import com.example.aiservice.dto.RecommendedVehicle;
import com.example.aiservice.entity.Estimate;
import com.example.aiservice.exception.AiServiceException;
import com.example.aiservice.repository.EstimateRepository;
import com.example.aiservice.service.EstimateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * Price and ETA are calculated deterministically from a real routing API - never
 * invented by the model (see PricingCalculator/EtaFormatter/VehicleRecommender). The
 * LLM is only called last, and only to explain the already-decided numbers in plain
 * language; it never calculates anything itself.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstimateServiceImpl implements EstimateService {

    private static final String NOT_PROVIDED = "Not provided";
    private static final String CURRENCY = "AMD";
    private static final long ESTIMATE_TTL_MINUTES = 30;

    /**
     * Deliberately gives the model the final numbers as facts and asks only for prose -
     * no JSON, no calculation. Earlier versions asked the model to also produce the
     * price/time/vehicle itself (as JSON); that was replaced because the model's numbers
     * were unreliable (e.g. always recommending TRUCK, or giving physically nonsensical
     * "1-2 days" ETAs for short routes) - see git history / prior task notes.
     */
    private static final String SYSTEM_PROMPT = """
            You are DeliveryOS's delivery assistant. A delivery estimate has already been
            calculated for the customer using real distance data and a fixed pricing
            formula - your only job is to explain it in friendly, plain language.

            Explain in under 80 words why this vehicle was selected and why the estimated
            price is reasonable, based on the distance, weight, and package description
            given. Use exactly the price and time you are given - do not invent or
            restate different numbers, and do not perform any calculation yourself.
            """;

    private final GeocodingClient geocodingClient;
    private final RoutingClient routingClient;
    private final VehicleRecommender vehicleRecommender;
    private final PricingCalculator pricingCalculator;
    private final EtaFormatter etaFormatter;
    private final OllamaClient ollamaClient;
    private final EstimateRepository estimateRepository;

    @Override
    public EstimateResponse estimate(EstimateRequest request) {
        Coordinate from = geocodingClient.geocode(request.getFromAddress());
        Coordinate to = geocodingClient.geocode(request.getToAddress());
        RouteResult route = routingClient.route(from, to);

        RecommendedVehicle vehicle = vehicleRecommender.recommend(request.getWeightKg(), request.getPackageDescription());
        double price = pricingCalculator.calculate(route.distanceKm(), request.getWeightKg(), vehicle);
        String eta = etaFormatter.format(route.durationMinutes(), vehicle);

        String explanation = ollamaClient.generate(SYSTEM_PROMPT, buildExplanationPrompt(request, route, vehicle, price, eta));

        Estimate estimate = persistEstimate(request, price, eta, vehicle);

        return new EstimateResponse(estimate.getId(), price, eta, vehicle, explanation, CURRENCY);
    }

    /**
     * EstimateRepository.claim() is a @Modifying query - Spring Data JPA requires an
     * active transaction to execute an UPDATE/DELETE query at all (it throws
     * InvalidDataAccessApiUsageException otherwise, regardless of how the update itself
     * is written), unlike a plain read.
     */
    @Override
    @Transactional
    public EstimateClaimResponse claimEstimate(UUID estimateId) {
        int claimed = estimateRepository.claim(estimateId);
        if (claimed == 0) {
            Estimate existing = estimateRepository.findById(estimateId).orElse(null);
            if (existing == null) {
                throw new AiServiceException(HttpStatus.NOT_FOUND, "Estimate not found: " + estimateId);
            }
            if (existing.isConsumed()) {
                throw new AiServiceException(HttpStatus.CONFLICT, "Estimate already claimed: " + estimateId);
            }
            throw new AiServiceException(HttpStatus.GONE, "Estimate expired: " + estimateId);
        }

        Estimate estimate = estimateRepository.findById(estimateId).orElseThrow();
        log.info("Estimate {} claimed", estimateId);

        return new EstimateClaimResponse(
                estimate.getId(), estimate.getFromAddress(), estimate.getToAddress(),
                estimate.getPackageDescription(), estimate.getWeightKg(), estimate.getEstimatedPrice(),
                estimate.getCurrency(), estimate.getEstimatedDeliveryTime(), estimate.getRecommendedVehicle());
    }

    private Estimate persistEstimate(EstimateRequest request, double price, String eta, RecommendedVehicle vehicle) {
        Estimate estimate = new Estimate();
        estimate.setId(UUID.randomUUID());
        estimate.setFromAddress(request.getFromAddress());
        estimate.setToAddress(request.getToAddress());
        estimate.setPackageDescription(request.getPackageDescription());
        estimate.setWeightKg(request.getWeightKg());
        estimate.setEstimatedPrice(price);
        estimate.setEstimatedDeliveryTime(eta);
        estimate.setRecommendedVehicle(vehicle);
        estimate.setCurrency(CURRENCY);
        estimate.setCreatedAt(LocalDateTime.now());
        estimate.setExpiresAt(LocalDateTime.now().plusMinutes(ESTIMATE_TTL_MINUTES));
        estimate.setConsumed(false);
        return estimateRepository.save(estimate);
    }

    private String buildExplanationPrompt(
            EstimateRequest request, RouteResult route, RecommendedVehicle vehicle, double price, String eta) {
        return String.format(Locale.ROOT, """
                Distance: %.1f km
                Estimated time: %s
                Vehicle: %s
                Price: %.0f AMD
                Weight: %s kg
                Package description: %s
                """,
                route.distanceKm(), eta, vehicle, price,
                request.getWeightKg(), orNotProvided(request.getPackageDescription()));
    }

    private String orNotProvided(String value) {
        return StringUtils.hasText(value) ? value : NOT_PROVIDED;
    }
}