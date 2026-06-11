package com.example.trackingservice.consumer;

import com.example.trackingservice.TrackingEvent;
import com.example.trackingservice.TrackingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class TrackingConsumer {

    private final TrackingEventRepository trackingEventRepository;

    @KafkaListener(topics = "delivery-updates", groupId = "tracking-group")
    public void listen(String message) {

        String[] parts = message.split(":");

        TrackingEvent event = new TrackingEvent();
        event.setOrderId(Long.parseLong(parts[0]));
        event.setCourierName(parts[1]);
        event.setStatus(parts[2]);
        event.setEventTime(LocalDateTime.now());

        trackingEventRepository.save(event);

        System.out.println("📍 Tracking saved: " + message);
    }
}