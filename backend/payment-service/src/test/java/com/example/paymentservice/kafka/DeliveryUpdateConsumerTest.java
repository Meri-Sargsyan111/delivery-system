package com.example.paymentservice.kafka;

import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.event.DeliveryUpdateEvent;
import com.example.paymentservice.repository.PaymentRepository;
import com.example.paymentservice.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeliveryUpdateConsumerTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;

    private DeliveryUpdateConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new DeliveryUpdateConsumer(paymentRepository, paymentService);
    }

    @Test
    void consume_cancelledOrderWithSucceededPayment_triggersRefund() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setStatus(PaymentStatus.SUCCEEDED);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));

        consumer.consume(new DeliveryUpdateEvent(10L, "Alice", "CANCELLED", null));

        verify(paymentService).refund(payment.getId(), null, "Order cancelled");
    }

    @Test
    void consume_cancelledOrderWithNoPayment_doesNothing() {
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.empty());

        consumer.consume(new DeliveryUpdateEvent(10L, "Alice", "CANCELLED", null));

        verify(paymentService, never()).refund(any(), any(), any());
    }

    @Test
    void consume_cancelledOrderWithAlreadyRefundedPayment_doesNotRefundAgain() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setStatus(PaymentStatus.REFUNDED);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));

        consumer.consume(new DeliveryUpdateEvent(10L, "Alice", "CANCELLED", null));

        verify(paymentService, never()).refund(any(), any(), any());
    }

    @Test
    void consume_nonCancelledStatus_isIgnored() {
        consumer.consume(new DeliveryUpdateEvent(10L, "Alice", "IN_PROGRESS", null));

        verify(paymentRepository, never()).findByOrderId(any());
    }
}
