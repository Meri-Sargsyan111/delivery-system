package com.example.courierservice.service;

import com.example.courierservice.client.TrackingServiceClient;
import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.dto.RouteView;
import com.example.courierservice.service.impl.LocationSimulatorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationSimulatorServiceImplTest {

    private static final Long COURIER_ID = 5L;
    private static final UUID COURIER_USER_ID = UUID.randomUUID();

    /** A short straight "route" - enough segments to exercise interpolation without real OSRM. */
    private static final RouteView STRAIGHT_ROUTE = new RouteView(
            List.of(
                    List.of(40.0000, 44.0000),
                    List.of(40.1000, 44.0000),
                    List.of(40.2000, 44.0000)
            ),
            22.24,
            33.0
    );

    @Mock private LocationService locationService;
    @Mock private TrackingServiceClient trackingServiceClient;

    private LocationSimulatorServiceImpl locationSimulatorService;
    private final AtomicLong clock = new AtomicLong(1_000_000L);

    @BeforeEach
    void setUp() {
        locationSimulatorService = new LocationSimulatorServiceImpl(locationService, trackingServiceClient);
        locationSimulatorService.setClockMillis(clock::get);
        locationSimulatorService.setSpeedKmh(36.0);
    }

    @Test
    void simulateMovement_whenNoOrderIsBeingTracked_publishesNothing() {
        locationSimulatorService.simulateMovement();

        verifyNoInteractions(locationService);
    }

    @Test
    void startTracking_whenRouteUnavailable_doesNotStartSimulation() {
        when(trackingServiceClient.getRoute(2L)).thenReturn(null);

        locationSimulatorService.startTracking(2L, COURIER_ID, COURIER_USER_ID);
        locationSimulatorService.simulateMovement();

        verifyNoInteractions(locationService);
    }

    @Test
    void simulateMovement_afterStartTracking_publishesLocationForThatRealOrderId() {
        when(trackingServiceClient.getRoute(2L)).thenReturn(STRAIGHT_ROUTE);

        locationSimulatorService.startTracking(2L, COURIER_ID, COURIER_USER_ID);
        locationSimulatorService.simulateMovement();

        ArgumentCaptor<CourierLocation> captor = ArgumentCaptor.forClass(CourierLocation.class);
        verify(locationService).sendLocation(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(2L);
    }

    @Test
    void simulateMovement_asTimeAdvances_movesFartherAlongTheRoute() {
        when(trackingServiceClient.getRoute(2L)).thenReturn(STRAIGHT_ROUTE);

        locationSimulatorService.startTracking(2L, COURIER_ID, COURIER_USER_ID);

        clock.addAndGet(60_000);
        locationSimulatorService.simulateMovement();

        clock.addAndGet(60_000);
        locationSimulatorService.simulateMovement();

        ArgumentCaptor<CourierLocation> captor = ArgumentCaptor.forClass(CourierLocation.class);
        verify(locationService, times(2)).sendLocation(captor.capture());

        var locations = captor.getAllValues();
        assertThat(locations.get(1).getLatitude()).isGreaterThan(locations.get(0).getLatitude());
    }

    @Test
    void simulateMovement_pastRouteEnd_clampsToFinalPoint() {
        when(trackingServiceClient.getRoute(2L)).thenReturn(STRAIGHT_ROUTE);

        locationSimulatorService.startTracking(2L, COURIER_ID, COURIER_USER_ID);
        clock.addAndGet(60 * 60_000L);

        locationSimulatorService.simulateMovement();

        ArgumentCaptor<CourierLocation> captor = ArgumentCaptor.forClass(CourierLocation.class);
        verify(locationService).sendLocation(captor.capture());
        assertThat(captor.getValue().getLatitude()).isEqualTo(40.2000, within(1e-6));
    }

    @Test
    void stopTracking_matchingActiveOrder_stopsFurtherPublishing() {
        when(trackingServiceClient.getRoute(2L)).thenReturn(STRAIGHT_ROUTE);

        locationSimulatorService.startTracking(2L, COURIER_ID, COURIER_USER_ID);
        locationSimulatorService.stopTracking(2L);

        locationSimulatorService.simulateMovement();

        verifyNoInteractions(locationService);
    }

    @Test
    void stopTracking_withDifferentOrderId_doesNotAffectOtherTrackedOrder() {
        when(trackingServiceClient.getRoute(2L)).thenReturn(STRAIGHT_ROUTE);

        locationSimulatorService.startTracking(2L, COURIER_ID, COURIER_USER_ID);
        locationSimulatorService.stopTracking(999L);

        locationSimulatorService.simulateMovement();

        ArgumentCaptor<CourierLocation> captor = ArgumentCaptor.forClass(CourierLocation.class);
        verify(locationService).sendLocation(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(2L);
    }

    @Test
    void simulateMovement_withTwoConcurrentOrders_advancesBothIndependently() {
        when(trackingServiceClient.getRoute(1L)).thenReturn(STRAIGHT_ROUTE);
        when(trackingServiceClient.getRoute(2L)).thenReturn(STRAIGHT_ROUTE);

        locationSimulatorService.startTracking(1L, COURIER_ID, COURIER_USER_ID);
        clock.addAndGet(60_000);
        locationSimulatorService.startTracking(2L, 6L, UUID.randomUUID());

        locationSimulatorService.simulateMovement();

        ArgumentCaptor<CourierLocation> captor = ArgumentCaptor.forClass(CourierLocation.class);
        verify(locationService, times(2)).sendLocation(captor.capture());

        var byOrder = captor.getAllValues();
        double order1Lat = byOrder.stream().filter(l -> l.getOrderId().equals(1L)).findFirst().orElseThrow().getLatitude();
        double order2Lat = byOrder.stream().filter(l -> l.getOrderId().equals(2L)).findFirst().orElseThrow().getLatitude();

        assertThat(order1Lat).isGreaterThan(order2Lat);
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
