package com.example.aiservice.controller;

import com.example.aiservice.dto.EstimateClaimResponse;
import com.example.aiservice.dto.EstimateRequest;
import com.example.aiservice.dto.EstimateResponse;
import com.example.aiservice.service.EstimateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Estimation only - deliberately does not create an order or call order-service. See
 * EstimateServiceImpl/OllamaClient for where the estimate actually comes from. The
 * estimate IS now persisted (see entity.Estimate) specifically so payment-service can
 * later claim it as the authoritative charge amount - see /estimate/{id}/claim below.
 */
@Slf4j
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class EstimateController {

    private final EstimateService estimateService;

    @PostMapping("/estimate")
    public ResponseEntity<EstimateResponse> estimate(@Valid @RequestBody EstimateRequest request) {
        log.info("POST /ai/estimate");
        return ResponseEntity.ok(estimateService.estimate(request));
    }

    /**
     * Single-use: the first caller to claim a given estimateId gets the frozen quote,
     * every subsequent call gets 409 (already claimed) or 410 (expired). Called by
     * payment-service at payment-creation time - never by an end-user client directly.
     */
    @PostMapping("/estimate/{estimateId}/claim")
    public ResponseEntity<EstimateClaimResponse> claim(@PathVariable UUID estimateId) {
        log.info("POST /ai/estimate/{}/claim", estimateId);
        return ResponseEntity.ok(estimateService.claimEstimate(estimateId));
    }
}