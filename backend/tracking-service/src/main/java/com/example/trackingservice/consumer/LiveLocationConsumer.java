package com.example.trackingservice.consumer;

import com.example.trackingservice.event.CourierLocationEvent;
import com.example.trackingservice.service.TrackingStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes courier-service's real-time GPS stream and drives TrackingState (route
 * matching, ETA recalculation, phase inference) - see TrackingStateService.onLocationUpdate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiveLocationConsumer {

    private final TrackingStateService trackingStateService;

    @KafkaListener(topics = "courier-location-updates", groupId = "tracking-group",
            containerFactory = "courierLocationKafkaListenerContainerFactory")
    public void listen(CourierLocationEvent event) {
        log.debug("Location update received: orderId={}, lat={}, lon={}",
                event.getOrderId(), event.getLatitude(), event.getLongitude());
        trackingStateService.onLocationUpdate(event);
    }
}
