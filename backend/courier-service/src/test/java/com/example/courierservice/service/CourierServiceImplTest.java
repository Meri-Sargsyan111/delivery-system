package com.example.courierservice.service;

import com.example.courierservice.entity.CourierUpdate;
import com.example.courierservice.event.DeliveryUpdateEvent;
import com.example.courierservice.repository.CourierUpdateRepository;
import com.example.courierservice.service.impl.CourierServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourierServiceImplTest {

    @Mock private CourierUpdateRepository courierUpdateRepository;
    @Mock private KafkaTemplate<String, DeliveryUpdateEvent> kafkaTemplate;

    @InjectMocks private CourierServiceImpl courierService;

    @Test
    void startDelivery_persistsUpdateRecordWithCorrectFields() {
        courierService.startDelivery(10L);

        ArgumentCaptor<CourierUpdate> captor = ArgumentCaptor.forClass(CourierUpdate.class);
        verify(courierUpdateRepository).save(captor.capture());

        CourierUpdate saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(10L);
        assertThat(saved.getCourierName()).isEqualTo("System");
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void startDelivery_publishesCorrectEventToKafka() {
        courierService.startDelivery(10L);

        ArgumentCaptor<DeliveryUpdateEvent> captor = ArgumentCaptor.forClass(DeliveryUpdateEvent.class);
        verify(kafkaTemplate).send(eq("delivery-updates"), captor.capture());

        DeliveryUpdateEvent event = captor.getValue();
        assertThat(event.getOrderId()).isEqualTo(10L);
        assertThat(event.getCourierName()).isEqualTo("System");
        assertThat(event.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void startDelivery_returnsDeliveryStartedMessage() {
        String result = courierService.startDelivery(10L);

        assertThat(result).isEqualTo("Delivery started");
    }

    @Test
    void startDelivery_whenRepositoryThrows_propagatesExceptionAndSkipsKafka() {
        when(courierUpdateRepository.save(any(CourierUpdate.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> courierService.startDelivery(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");

        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void startDelivery_whenKafkaThrows_propagatesExceptionAfterSave() {
        when(kafkaTemplate.send(eq("delivery-updates"), any(DeliveryUpdateEvent.class)))
                .thenThrow(new RuntimeException("Kafka unavailable"));

        assertThatThrownBy(() -> courierService.startDelivery(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Kafka unavailable");

        verify(courierUpdateRepository).save(any(CourierUpdate.class));
    }



    @Test
    void markAsDelivered_persistsUpdateRecordWithCorrectFields() {
        courierService.markAsDelivered(20L);

        ArgumentCaptor<CourierUpdate> captor = ArgumentCaptor.forClass(CourierUpdate.class);
        verify(courierUpdateRepository).save(captor.capture());

        CourierUpdate saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(20L);
        assertThat(saved.getCourierName()).isEqualTo("System");
        assertThat(saved.getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void markAsDelivered_publishesCorrectEventToKafka() {
        courierService.markAsDelivered(20L);

        ArgumentCaptor<DeliveryUpdateEvent> captor = ArgumentCaptor.forClass(DeliveryUpdateEvent.class);
        verify(kafkaTemplate).send(eq("delivery-updates"), captor.capture());

        DeliveryUpdateEvent event = captor.getValue();
        assertThat(event.getOrderId()).isEqualTo(20L);
        assertThat(event.getCourierName()).isEqualTo("System");
        assertThat(event.getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void markAsDelivered_returnsDeliveryCompletedMessage() {
        String result = courierService.markAsDelivered(20L);

        assertThat(result).isEqualTo("Delivery completed");
    }

    @Test
    void markAsDelivered_whenRepositoryThrows_propagatesExceptionAndSkipsKafka() {
        when(courierUpdateRepository.save(any(CourierUpdate.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> courierService.markAsDelivered(20L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");

        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void markAsDelivered_whenKafkaThrows_propagatesExceptionAfterSave() {
        when(kafkaTemplate.send(eq("delivery-updates"), any(DeliveryUpdateEvent.class)))
                .thenThrow(new RuntimeException("Kafka unavailable"));

        assertThatThrownBy(() -> courierService.markAsDelivered(20L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Kafka unavailable");

        verify(courierUpdateRepository).save(any(CourierUpdate.class));
    }
}