package com.example.courierservice.service;

import com.example.courierservice.client.OrderServiceClient;
import com.example.courierservice.dto.AssignmentResponse;
import com.example.courierservice.dto.RemoteOrderView;
import com.example.courierservice.entity.CourierUpdate;
import com.example.courierservice.event.DeliveryUpdateEvent;
import com.example.courierservice.exception.InvalidOrderStateException;
import com.example.courierservice.repository.CourierUpdateRepository;
import com.example.courierservice.security.CurrentUser;
import com.example.courierservice.service.impl.CourierServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourierServiceImplTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID COURIER_USER_ID = UUID.randomUUID();
    private static final UUID OTHER_COURIER_USER_ID = UUID.randomUUID();

    @Mock private CourierUpdateRepository courierUpdateRepository;
    @Mock private KafkaTemplate<String, DeliveryUpdateEvent> kafkaTemplate;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private CourierAssignmentService courierAssignmentService;
    @Mock private LocationSimulatorService locationSimulatorService;
    @Mock private CurrentUser currentUser;

    @InjectMocks private CourierServiceImpl courierService;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.isAdmin()).thenReturn(true);
        lenient().when(currentUser.isCourier()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(ADMIN_ID);
    }

    private void asCourier(UUID userId) {
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.isCourier()).thenReturn(true);
        lenient().when(currentUser.getUserId()).thenReturn(userId);
    }

    @Test
    void startDelivery_whenOrderAssigned_persistsUpdateRecordWithCourierName() {
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "ASSIGNED", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));

        courierService.startDelivery(10L);

        ArgumentCaptor<CourierUpdate> captor = ArgumentCaptor.forClass(CourierUpdate.class);
        verify(courierUpdateRepository).save(captor.capture());

        CourierUpdate saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(10L);
        assertThat(saved.getCourierName()).isEqualTo("Alice Johnson");
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void startDelivery_whenOrderAssigned_publishesCorrectEventToKafka() {
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "ASSIGNED", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));

        courierService.startDelivery(10L);

        ArgumentCaptor<DeliveryUpdateEvent> captor = ArgumentCaptor.forClass(DeliveryUpdateEvent.class);
        verify(kafkaTemplate).send(eq("delivery-updates"), captor.capture());

        DeliveryUpdateEvent event = captor.getValue();
        assertThat(event.getOrderId()).isEqualTo(10L);
        assertThat(event.getCourierName()).isEqualTo("Alice Johnson");
        assertThat(event.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void startDelivery_whenOrderAssigned_startsLiveLocationTrackingForRealOrderId() {
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "ASSIGNED", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));

        courierService.startDelivery(10L);

        verify(locationSimulatorService).startTracking(10L);
    }

    @Test
    void startDelivery_whenOrderNotAssigned_doesNotStartLiveLocationTracking() {
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "CREATED", null, COURIER_USER_ID));

        assertThatThrownBy(() -> courierService.startDelivery(10L))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(locationSimulatorService, never()).startTracking(any());
    }

    @Test
    void startDelivery_whenOrderAssigned_returnsDeliveryStartedMessage() {
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "ASSIGNED", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));

        String result = courierService.startDelivery(10L);

        assertThat(result).isEqualTo("Delivery started");
    }

    @Test
    void startDelivery_whenOrderNotAssigned_throwsInvalidOrderStateAndSkipsPersistence() {
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "CREATED", null, COURIER_USER_ID));

        assertThatThrownBy(() -> courierService.startDelivery(10L))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(courierUpdateRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void startDelivery_whenRepositoryThrows_propagatesExceptionAndSkipsKafka() {
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "ASSIGNED", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));
        when(courierUpdateRepository.save(any(CourierUpdate.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> courierService.startDelivery(10L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");

        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void markAsDelivered_whenInProgress_persistsUpdateRecordAndReleasesCourier() {
        when(orderServiceClient.getOrder(20L)).thenReturn(new RemoteOrderView(20L, "IN_PROGRESS", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(20L))
                .thenReturn(new AssignmentResponse(20L, 5L, "Alice Johnson", null));

        courierService.markAsDelivered(20L);

        ArgumentCaptor<CourierUpdate> captor = ArgumentCaptor.forClass(CourierUpdate.class);
        verify(courierUpdateRepository).save(captor.capture());

        CourierUpdate saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(20L);
        assertThat(saved.getCourierName()).isEqualTo("Alice Johnson");
        assertThat(saved.getStatus()).isEqualTo("DELIVERED");

        verify(courierAssignmentService).releaseCourierForOrder(20L);
        verify(locationSimulatorService).stopTracking(20L);
    }

    @Test
    void markAsDelivered_whenInProgress_publishesCorrectEventToKafka() {
        when(orderServiceClient.getOrder(20L)).thenReturn(new RemoteOrderView(20L, "IN_PROGRESS", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(20L))
                .thenReturn(new AssignmentResponse(20L, 5L, "Alice Johnson", null));

        courierService.markAsDelivered(20L);

        ArgumentCaptor<DeliveryUpdateEvent> captor = ArgumentCaptor.forClass(DeliveryUpdateEvent.class);
        verify(kafkaTemplate).send(eq("delivery-updates"), captor.capture());

        DeliveryUpdateEvent event = captor.getValue();
        assertThat(event.getOrderId()).isEqualTo(20L);
        assertThat(event.getCourierName()).isEqualTo("Alice Johnson");
        assertThat(event.getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void markAsDelivered_whenNotYetStarted_throwsInvalidOrderStateAndDoesNotReleaseCourier() {
        when(orderServiceClient.getOrder(20L)).thenReturn(new RemoteOrderView(20L, "CREATED", null, COURIER_USER_ID));

        assertThatThrownBy(() -> courierService.markAsDelivered(20L))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(courierUpdateRepository, never()).save(any());
        verify(courierAssignmentService, never()).releaseCourierForOrder(any());
        verify(locationSimulatorService, never()).stopTracking(any());
    }

    @Test
    void markAsDelivered_whenAlreadyDelivered_throwsInvalidOrderState() {
        when(orderServiceClient.getOrder(20L)).thenReturn(new RemoteOrderView(20L, "DELIVERED", null, COURIER_USER_ID));

        assertThatThrownBy(() -> courierService.markAsDelivered(20L))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void markAsDelivered_whenRepositoryThrows_propagatesExceptionAndSkipsKafka() {
        when(orderServiceClient.getOrder(20L)).thenReturn(new RemoteOrderView(20L, "IN_PROGRESS", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(20L))
                .thenReturn(new AssignmentResponse(20L, 5L, "Alice Johnson", null));
        when(courierUpdateRepository.save(any(CourierUpdate.class)))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> courierService.markAsDelivered(20L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");

        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void startDelivery_asAssignedCourier_succeeds() {
        asCourier(COURIER_USER_ID);
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "ASSIGNED", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));

        String result = courierService.startDelivery(10L);

        assertThat(result).isEqualTo("Delivery started");
    }

    @Test
    void startDelivery_asUnrelatedCourier_throwsAccessDenied() {
        asCourier(OTHER_COURIER_USER_ID);
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "ASSIGNED", null, COURIER_USER_ID));

        assertThatThrownBy(() -> courierService.startDelivery(10L))
                .isInstanceOf(AccessDeniedException.class);

        verify(courierUpdateRepository, never()).save(any());
    }

    @Test
    void markAsDelivered_asAssignedCourier_succeeds() {
        asCourier(COURIER_USER_ID);
        when(orderServiceClient.getOrder(20L)).thenReturn(new RemoteOrderView(20L, "IN_PROGRESS", null, COURIER_USER_ID));
        when(courierAssignmentService.getAssignment(20L))
                .thenReturn(new AssignmentResponse(20L, 5L, "Alice Johnson", null));

        courierService.markAsDelivered(20L);

        verify(courierUpdateRepository).save(any(CourierUpdate.class));
    }

    @Test
    void markAsDelivered_asUnrelatedCourier_throwsAccessDenied() {
        asCourier(OTHER_COURIER_USER_ID);
        when(orderServiceClient.getOrder(20L)).thenReturn(new RemoteOrderView(20L, "IN_PROGRESS", null, COURIER_USER_ID));

        assertThatThrownBy(() -> courierService.markAsDelivered(20L))
                .isInstanceOf(AccessDeniedException.class);

        verify(courierUpdateRepository, never()).save(any());
        verify(courierAssignmentService, never()).releaseCourierForOrder(any());
    }
}
