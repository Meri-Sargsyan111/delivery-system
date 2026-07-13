package com.example.courierservice.kafka;

import com.example.courierservice.event.DeliveryUpdateEvent;
import com.example.courierservice.service.CourierAssignmentService;
import com.example.courierservice.service.LocationSimulatorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryUpdateConsumerTest {

    @Mock private CourierAssignmentService courierAssignmentService;
    @Mock private LocationSimulatorService locationSimulatorService;

    @InjectMocks private DeliveryUpdateConsumer deliveryUpdateConsumer;

    @Test
    void consume_whenCancelled_releasesCourierAndStopsLocationTracking() {
        deliveryUpdateConsumer.consume(new DeliveryUpdateEvent(7L, "Alice Johnson", "CANCELLED", null));

        verify(courierAssignmentService).releaseCourierForOrder(7L);
        verify(locationSimulatorService).stopTracking(7L);
    }

    @Test
    void consume_whenNotCancelled_doesNothing() {
        deliveryUpdateConsumer.consume(new DeliveryUpdateEvent(7L, "Alice Johnson", "IN_PROGRESS", null));

        verify(courierAssignmentService, never()).releaseCourierForOrder(any());
        verify(locationSimulatorService, never()).stopTracking(any());
    }
}