package com.example.paymentservice.kafka;

import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.event.DeliveryUpdateEvent;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Reacts to the EXISTING delivery-updates topic (order-service is the producer, already
 * consumed by five other services - joining as our own consumer group, payment-group,
 * has no interference with them). On CANCELLED where a SUCCEEDED payment exists for that
 * order, triggers a refund - payment-service owns the refund decision, order-service's
 * cancellation is just the trigger. The event payload doesn't carry the order's prior
 * status (only the new one), so this can't distinguish "cancelled before courier
 * dispatch" from "cancelled mid-delivery" - it refunds any CANCELLED order that was
 * actually paid for, which is the conservative choice given the information available
 * (order-service already refuses to cancel a DELIVERED order, so this never fires late).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryUpdateConsumer {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @KafkaListener(topics = "delivery-updates", groupId = "payment-group",
            containerFactory = "deliveryUpdateKafkaListenerContainerFactory")
    public void consume(DeliveryUpdateEvent event) {
        if (!"CANCELLED".equals(event.getStatus()) || event.getOrderId() == null) {
            return;
        }

        Payment payment = paymentRepository.findByOrderId(event.getOrderId()).orElse(null);
        if (payment == null) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            log.info("Order {} cancelled but its payment {} is {}, not SUCCEEDED - no refund needed",
                    event.getOrderId(), payment.getId(), payment.getStatus());
            return;
        }

        log.info("Order {} cancelled - auto-refunding payment {}", event.getOrderId(), payment.getId());
        paymentService.refund(payment.getId(), null, "Order cancelled");
    }
}
