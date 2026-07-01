package com.example.trackingservice.mapper;

import com.example.trackingservice.dto.TrackingEventResponse;
import com.example.trackingservice.entity.TrackingEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TrackingEventMapper {

    public TrackingEventResponse toResponse(TrackingEvent event) {
        if (event == null) {
            return null;
        }
        return new TrackingEventResponse(
                event.getId(),
                event.getOrderId(),
                event.getCourierName(),
                event.getStatus(),
                event.getEventTime()
        );
    }

    public List<TrackingEventResponse> toResponseList(List<TrackingEvent> events) {
        return events.stream()
                .map(this::toResponse)
                .toList();
    }
}