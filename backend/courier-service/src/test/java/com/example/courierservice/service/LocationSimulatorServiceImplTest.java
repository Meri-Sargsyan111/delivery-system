package com.example.courierservice.service;

import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.service.impl.LocationSimulatorServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LocationSimulatorServiceImplTest {

    @Mock private LocationService locationService;

    @InjectMocks private LocationSimulatorServiceImpl locationSimulatorService;

    @Test
    void simulateMovement_whenNoOrderIsBeingTracked_publishesNothing() {
        locationSimulatorService.simulateMovement();

        verifyNoInteractions(locationService);
    }

    @Test
    void simulateMovement_afterStartTracking_publishesLocationsForThatRealOrderId() {
        locationSimulatorService.startTracking(2L);

        locationSimulatorService.simulateMovement();

        ArgumentCaptor<CourierLocation> captor = ArgumentCaptor.forClass(CourierLocation.class);
        verify(locationService).sendLocation(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(2L);
    }

    @Test
    void simulateMovement_calledRepeatedly_advancesThroughRouteForTheSameOrder() {
        locationSimulatorService.startTracking(2L);

        locationSimulatorService.simulateMovement();
        locationSimulatorService.simulateMovement();

        ArgumentCaptor<CourierLocation> captor = ArgumentCaptor.forClass(CourierLocation.class);
        verify(locationService, times(2)).sendLocation(captor.capture());

        var locations = captor.getAllValues();
        assertThat(locations.get(0).getOrderId()).isEqualTo(2L);
        assertThat(locations.get(1).getOrderId()).isEqualTo(2L);
        assertThat(locations.get(0).getLatitude()).isNotEqualTo(locations.get(1).getLatitude());
    }

    @Test
    void stopTracking_matchingActiveOrder_stopsFurtherPublishing() {
        locationSimulatorService.startTracking(2L);
        locationSimulatorService.stopTracking(2L);

        locationSimulatorService.simulateMovement();

        verifyNoInteractions(locationService);
    }

    @Test
    void stopTracking_withDifferentOrderId_doesNotAffectCurrentlyTrackedOrder() {
        locationSimulatorService.startTracking(2L);
        locationSimulatorService.stopTracking(999L);

        locationSimulatorService.simulateMovement();

        ArgumentCaptor<CourierLocation> captor = ArgumentCaptor.forClass(CourierLocation.class);
        verify(locationService).sendLocation(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(2L);
    }

    @Test
    void startTracking_forNewOrder_switchesActiveOrderAndRestartsRoute() {
        locationSimulatorService.startTracking(1L);
        locationSimulatorService.simulateMovement();
        locationSimulatorService.simulateMovement();

        locationSimulatorService.startTracking(2L);
        locationSimulatorService.simulateMovement();

        ArgumentCaptor<CourierLocation> captor = ArgumentCaptor.forClass(CourierLocation.class);
        verify(locationService, times(3)).sendLocation(captor.capture());

        var locations = captor.getAllValues();
        assertThat(locations.get(0).getOrderId()).isEqualTo(1L);
        assertThat(locations.get(1).getOrderId()).isEqualTo(1L);
        assertThat(locations.get(2).getOrderId()).isEqualTo(2L);

        assertThat(locations.get(2).getLatitude()).isEqualTo(locations.get(0).getLatitude());
    }
}