package com.example.courierservice.kafka;

import com.example.courierservice.event.CourierRegisteredEvent;
import com.example.courierservice.service.CourierAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Creates the linked Courier profile when a ROLE_COURIER user registers in
 * auth-service, so the account shows up in Admin Courier Management without
 * requiring a separate manual admin step.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierRegistrationConsumer {

    private final CourierAssignmentService courierAssignmentService;

    @KafkaListener(topics = "courier-registered", groupId = "courier-group",
            containerFactory = "courierRegisteredKafkaListenerContainerFactory")
    public void consume(CourierRegisteredEvent event) {
        String name = (event.getFirstName() + " " + event.getLastName()).trim();
        courierAssignmentService.registerCourierAccount(event.getUserId(), name);
        log.info("Processed courier-registered event for user {}", event.getUserId());
    }
}
