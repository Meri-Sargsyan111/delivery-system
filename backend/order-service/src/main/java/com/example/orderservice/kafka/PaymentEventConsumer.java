package com.example.orderservice.kafka;

import com.example.orderservice.event.PaymentLifecycleEvent;
import com.example.orderservice.exception.InvalidOrderStateException;
import com.example.orderservice.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Reacts to payment-service's payment-events topic: on REFUNDED, cancels the associated
 * order (reuses the existing CANCELLED status rather than adding a new OrderStatus value
 * - see OrderStatus). Calls the EXISTING OrderService.cancelOrder rather than duplicating
 * its state-transition/Kafka-publish logic - but that method's ownership check
 * (requireAdminOrOwningCustomer) reads SecurityContextHolder, which a Kafka consumer
 * thread never populates. A short-lived system principal with ROLE_ADMIN is set for the
 * duration of this call only (cleared in finally) - the standard pattern for a
 * background/system-initiated action that must satisfy the same authorization gate a
 * real admin call would, without forking a second "internal" cancel method.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final OrderService orderService;

    @KafkaListener(topics = "payment-events", groupId = "order-group",
            containerFactory = "paymentLifecycleKafkaListenerContainerFactory")
    public void consume(PaymentLifecycleEvent event) {
        if (!"REFUNDED".equals(event.getStatus()) || event.getOrderId() == null) {
            return;
        }

        runAsSystem(() -> {
            try {
                orderService.cancelOrder(event.getOrderId());
                log.info("Order {} cancelled following refund of payment {}", event.getOrderId(), event.getPaymentId());
            } catch (InvalidOrderStateException ex) {
                log.info("Order {} already in a terminal state, refund of payment {} needs no order-side change: {}",
                        event.getOrderId(), event.getPaymentId(), ex.getMessage());
            }
        });
    }

    private void runAsSystem(Runnable action) {
        Authentication systemAuth = new UsernamePasswordAuthenticationToken(
                "payment-event-consumer", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        try {
            SecurityContextHolder.getContext().setAuthentication(systemAuth);
            action.run();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
