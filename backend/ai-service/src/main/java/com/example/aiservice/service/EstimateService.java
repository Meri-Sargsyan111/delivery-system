package com.example.aiservice.service;

import com.example.aiservice.dto.EstimateClaimResponse;
import com.example.aiservice.dto.EstimateRequest;
import com.example.aiservice.dto.EstimateResponse;

import java.util.UUID;

public interface EstimateService {

    EstimateResponse estimate(EstimateRequest request);

    /**
     * Atomically claims a previously-issued estimate (see entity.Estimate) - single use,
     * fails with a typed AiServiceException (404/409/410) if the estimate doesn't exist,
     * was already claimed, or has expired.
     */
    EstimateClaimResponse claimEstimate(UUID estimateId);
}