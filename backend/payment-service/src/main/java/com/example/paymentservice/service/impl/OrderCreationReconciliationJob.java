package com.example.paymentservice.service.impl;

import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The reliability backstop for payment->order-creation - inline retry inside
 * PaymentServiceImpl only covers a transient failure within one live request; this
 * covers payment-service crashing mid-flow, or every inline retry being exhausted.
 * See PaymentService.reconcileStuckPayments for the actual sweep logic.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreationReconciliationJob {

    private final PaymentService paymentService;

    @Scheduled(fixedDelayString = "${payment.order-creation.sweep-fixed-delay-ms:60000}")
    public void run() {
        try {
            paymentService.reconcileStuckPayments();
        } catch (Exception e) {
            log.error("Order-creation reconciliation sweep failed unexpectedly - will retry on next tick", e);
        }
    }
}
