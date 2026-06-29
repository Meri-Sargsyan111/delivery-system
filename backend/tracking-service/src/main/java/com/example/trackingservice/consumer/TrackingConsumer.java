package com.example.trackingservice.consumer;

import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.event.DeliveryUpdateEvent;
import com.example.trackingservice.repository.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackingConsumer {

    private final TrackingEventRepository trackingEventRepository;

    @KafkaListener(topics = "delivery-updates", groupId = "tracking-group",
            containerFactory = "deliveryUpdateKafkaListenerContainerFactory")
    public void listen(DeliveryUpdateEvent event) {

        TrackingEvent trackingEvent = new TrackingEvent();
        trackingEvent.setOrderId(event.getOrderId());
        trackingEvent.setCourierName(event.getCourierName());
        trackingEvent.setStatus(event.getStatus());
        trackingEvent.setEventTime(LocalDateTime.now());

        trackingEventRepository.save(trackingEvent);

        log.info("Tracking saved: orderId={}, status={}", event.getOrderId(), event.getStatus());
    }
}
