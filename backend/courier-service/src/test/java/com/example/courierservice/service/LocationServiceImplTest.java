package com.example.courierservice.service;

import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.courier.VehicleType;
import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.entity.Courier;
import com.example.courierservice.entity.CourierAssignment;
import com.example.courierservice.event.CourierLocationEvent;
import com.example.courierservice.repository.CourierAssignmentRepository;
import com.example.courierservice.repository.CourierRepository;
import com.example.courierservice.security.CurrentUser;
import com.example.courierservice.service.impl.LocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationServiceImplTest {

    private static final Long ORDER_ID = 2L;
    private static final Long COURIER_ID = 5L;
    private static final UUID COURIER_USER_ID = UUID.randomUUID();

    @Mock private KafkaTemplate<String, CourierLocationEvent> kafkaTemplate;
    @Mock private CourierAssignmentRepository courierAssignmentRepository;
    @Mock private CourierRepository courierRepository;
    @Mock private CurrentUser currentUser;

    private LocationServiceImpl locationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationServiceImpl(
                kafkaTemplate, courierAssignmentRepository, courierRepository, currentUser);
        setField("minIntervalMs", 5000L);
        setField("minDistanceMeters", 15.0);

        lenient().when(currentUser.isAuthenticated()).thenReturn(true);
        lenient().when(currentUser.isAdmin()).thenReturn(true);

        lenient().when(courierAssignmentRepository.findByOrderId(ORDER_ID))
                .thenReturn(Optional.of(new CourierAssignment(1L, ORDER_ID, COURIER_ID, LocalDateTime.now())));
        lenient().when(courierRepository.findById(COURIER_ID))
                .thenReturn(Optional.of(new Courier(COURIER_ID, "Alice", CourierStatus.BUSY, null, COURIER_USER_ID, VehicleType.CAR)));
    }

    private void setField(String name, Object value) {
        try {
            var field = LocationServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(locationService, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void sendLocation_firstUpdateForOrder_publishesToKafka() {
        locationService.sendLocation(new CourierLocation(ORDER_ID, 40.10, 44.50, 90, 40));

        ArgumentCaptor<CourierLocationEvent> captor = ArgumentCaptor.forClass(CourierLocationEvent.class);
        verify(kafkaTemplate).send(eq("courier-location-updates"), captor.capture());

        CourierLocationEvent event = captor.getValue();
        assertThat(event.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(event.getCourierId()).isEqualTo(COURIER_ID);
        assertThat(event.getCourierUserId()).isEqualTo(COURIER_USER_ID);
        assertThat(event.getLatitude()).isEqualTo(40.10);
    }

    @Test
    void sendLocation_negligibleMovementWithinMinInterval_isThrottledAndSkipped() {
        locationService.sendLocation(new CourierLocation(ORDER_ID, 40.10000, 44.50000, 90, 40));
        locationService.sendLocation(new CourierLocation(ORDER_ID, 40.10001, 44.50000, 90, 40));

        verify(kafkaTemplate, times(1)).send(eq("courier-location-updates"), any());
    }

    @Test
    void sendLocation_movedFarEnough_publishesEvenWithinMinInterval() {
        locationService.sendLocation(new CourierLocation(ORDER_ID, 40.1000, 44.5000, 90, 40));
        locationService.sendLocation(new CourierLocation(ORDER_ID, 40.1010, 44.5000, 90, 40));

        verify(kafkaTemplate, times(2)).send(eq("courier-location-updates"), any());
    }

    @Test
    void sendLocation_noAssignmentForOrder_dropsUpdateWithoutPublishing() {
        when(courierAssignmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        locationService.sendLocation(new CourierLocation(ORDER_ID, 40.10, 44.50, 90, 40));

        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void sendLocation_nonAdminNonAssignedCourier_throwsAccessDenied() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.isCourier()).thenReturn(true);
        when(currentUser.getUserId()).thenReturn(UUID.randomUUID());
        when(courierRepository.findByUserId(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> locationService.sendLocation(new CourierLocation(ORDER_ID, 40.10, 44.50, 90, 40)))
                .isInstanceOf(AccessDeniedException.class);

        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void sendLocation_unauthenticatedCaller_isTrustedLikeTheSimulator() {
        when(currentUser.isAuthenticated()).thenReturn(false);

        locationService.sendLocation(new CourierLocation(ORDER_ID, 40.10, 44.50, 90, 40));

        verify(kafkaTemplate).send(eq("courier-location-updates"), any());
    }
}
