package com.example.courierservice.kafka;

import com.example.courierservice.event.CourierRegisteredEvent;
import com.example.courierservice.service.CourierAssignmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourierRegistrationConsumerTest {

    @Mock private CourierAssignmentService courierAssignmentService;

    @InjectMocks private CourierRegistrationConsumer courierRegistrationConsumer;

    @Test
    void consume_delegatesToCourierAssignmentServiceWithJoinedName() {
        UUID userId = UUID.randomUUID();

        courierRegistrationConsumer.consume(new CourierRegisteredEvent(userId, "Jane", "Rider"));

        verify(courierAssignmentService).registerCourierAccount(userId, "Jane Rider");
    }
}
