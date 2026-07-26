package com.example.paymentservice.controller;

import com.example.paymentservice.dto.CreatePaymentRequest;
import com.example.paymentservice.dto.PaymentDetailsResponse;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.dto.RefundRequest;
import com.example.paymentservice.exception.IdempotencyKeyRequiredException;
import com.example.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final PaymentService paymentService;

    @PreAuthorize("hasRole('ADMIN') or hasRole('CUSTOMER')")
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IdempotencyKeyRequiredException(IDEMPOTENCY_KEY_HEADER + " header is required");
        }

        log.info("POST /payments - estimateId={}, paymentMethod={}", request.getEstimateId(), request.getPaymentMethod());
        return ResponseEntity.status(HttpStatus.CREATED).body(paymentService.createPayment(request, idempotencyKey));
    }

    @PostMapping("/{id}/verify")
    public ResponseEntity<PaymentResponse> verifyPayment(@PathVariable UUID id) {
        log.info("POST /payments/{}/verify", id);
        return ResponseEntity.ok(paymentService.verifyPayment(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentDetailsResponse> getPayment(@PathVariable UUID id) {
        log.info("GET /payments/{}", id);
        return ResponseEntity.ok(paymentService.getDetails(id));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<PaymentDetailsResponse>> getHistory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(paymentService.getHistory(pageable));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentDetailsResponse> refund(@PathVariable UUID id, @RequestBody(required = false) RefundRequest request) {
        BigDecimal amount = request != null ? request.getAmount() : null;
        String reason = request != null ? request.getReason() : null;
        log.info("POST /payments/{}/refund", id);
        return ResponseEntity.ok(paymentService.refund(id, amount, reason));
    }
}
