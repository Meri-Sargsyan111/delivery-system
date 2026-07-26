package com.example.paymentservice.client;

import com.example.paymentservice.client.dto.EstimateClaimResult;
import com.example.paymentservice.exception.EstimateClaimException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

/**
 * Claims a frozen estimate from ai-service at payment-creation time - the ONLY source of
 * the authoritative amount to charge (see EstimateClaimResult). This call runs inside the
 * customer's own authenticated POST /payments request, so the caller's JWT is forwarded
 * rather than introducing a separate service-to-service credential - ai-service already
 * requires authentication on every endpoint, and the customer's own token satisfies that.
 */
@Slf4j
@Component
public class AiServiceClient {

    private final RestTemplate restTemplate;
    private final String aiServiceBaseUrl;

    public AiServiceClient(RestTemplate restTemplate,
                            @Value("${services.ai-service.base-url}") String aiServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.aiServiceBaseUrl = aiServiceBaseUrl;
    }

    public EstimateClaimResult claimEstimate(UUID estimateId) {
        String url = aiServiceBaseUrl + "/ai/estimate/" + estimateId + "/claim";
        try {
            return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(authHeaders()), EstimateClaimResult.class)
                    .getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new EstimateClaimException(HttpStatus.NOT_FOUND, "Estimate not found: " + estimateId);
        } catch (HttpClientErrorException.Conflict ex) {
            throw new EstimateClaimException(HttpStatus.CONFLICT, "Estimate already claimed: " + estimateId);
        } catch (HttpClientErrorException.Gone ex) {
            throw new EstimateClaimException(HttpStatus.GONE, "Estimate expired: " + estimateId);
        } catch (HttpStatusCodeException ex) {
            log.error("ai-service returned an unexpected error claiming estimate {}: {}", estimateId, ex.getStatusCode());
            throw new EstimateClaimException(HttpStatus.SERVICE_UNAVAILABLE, "Estimate service returned an unexpected error");
        } catch (ResourceAccessException ex) {
            log.error("ai-service unreachable/timed out claiming estimate {}", estimateId, ex);
            throw new EstimateClaimException(HttpStatus.SERVICE_UNAVAILABLE, "Estimate service is temporarily unavailable");
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
