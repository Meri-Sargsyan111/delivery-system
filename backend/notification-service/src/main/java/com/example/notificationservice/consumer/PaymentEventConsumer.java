package com.example.notificationservice.consumer;

import com.example.notificationservice.entity.NotificationType;
import com.example.notificationservice.event.PaymentLifecycleEvent;
import com.example.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Reacts to payment-service's payment-events topic - customer-specific notification for
 * every terminal status, plus a null-recipient broadcast for failures (already treated
 * as admin-visible-only by NotificationServiceImpl's existing getNotifications() rule -
 * reusing that mechanism rather than adding a separate admin notification path).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final NotificationService notificationStore;

    @KafkaListener(topics = "payment-events", groupId = "notification-group",
            containerFactory = "paymentLifecycleKafkaListenerContainerFactory")
    public void consume(PaymentLifecycleEvent event) {
        log.info("Received payment event: paymentId={}, status={}", event.getPaymentId(), event.getStatus());

        switch (event.getStatus()) {
            case "SUCCEEDED" -> {
                String message = "Payment " + event.getPaymentId() + " succeeded: "
                        + event.getAmount() + " " + event.getCurrency();
                notificationStore.add(message, event.getCustomerId(), NotificationType.PAYMENT_SUCCEEDED, true);
            }
            case "FAILED" -> {
                String message = "Payment " + event.getPaymentId() + " failed for customer " + event.getCustomerId();
                notificationStore.add(message, event.getCustomerId(), NotificationType.PAYMENT_FAILED, true);
                notificationStore.add(message, null, NotificationType.PAYMENT_FAILED, false);
            }
            case "REFUNDED" -> {
                String message = "Payment " + event.getPaymentId() + " refunded: "
                        + event.getAmount() + " " + event.getCurrency();
                notificationStore.add(message, event.getCustomerId(), NotificationType.PAYMENT_REFUNDED, true);
            }
            default -> log.debug("Ignoring payment event with status={}", event.getStatus());
        }
    }
}
