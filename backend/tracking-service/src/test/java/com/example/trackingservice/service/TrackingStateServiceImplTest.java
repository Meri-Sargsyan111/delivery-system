package com.example.trackingservice.service;

import com.example.trackingservice.entity.DeliveryPhase;
import com.example.trackingservice.entity.TrackingState;
import com.example.trackingservice.event.CourierLocationEvent;
import com.example.trackingservice.exception.EntityNotFoundException;
import com.example.trackingservice.repository.TrackingEventRepository;
import com.example.trackingservice.repository.TrackingStateRepository;
import com.example.trackingservice.routing.GeocodingClient;
import com.example.trackingservice.routing.RoutingClient;
import com.example.trackingservice.routing.dto.Coordinate;
import com.example.trackingservice.routing.dto.RouteResult;
import com.example.trackingservice.security.CurrentUser;
import com.example.trackingservice.security.TrackingAccessGuard;
import com.example.trackingservice.service.impl.TrackingStateServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackingStateServiceImplTest {

    private static final Long ORDER_ID = 42L;

    @Mock private TrackingStateRepository trackingStateRepository;
    @Mock private TrackingEventRepository trackingEventRepository;
    @Mock private TrackingAccessGuard trackingAccessGuard;
    @Mock private CurrentUser currentUser;
    @Mock private GeocodingClient geocodingClient;
    @Mock private RoutingClient routingClient;
    @Mock private KafkaTemplate<String, com.example.trackingservice.event.TrackingLifecycleEvent> kafkaTemplate;
    @Mock private SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TrackingStateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TrackingStateServiceImpl(
                trackingStateRepository, trackingEventRepository, trackingAccessGuard, currentUser,
                geocodingClient, routingClient, objectMapper, kafkaTemplate, messagingTemplate);
        setField("pickupRadiusMeters", 150.0);
        setField("destinationRadiusMeters", 300.0);
        setField("routeDeviationThresholdMeters", 200.0);

        lenient().when(trackingStateRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void setField(String name, Object value) {
        try {
            var field = TrackingStateServiceImpl.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void onOrderCreated_newOrder_seedsWaitingForCourierAndGeocodesBothAddresses() {
        when(trackingStateRepository.existsById(ORDER_ID)).thenReturn(false);
        when(geocodingClient.geocode("Gyumri")).thenReturn(new Coordinate(40.7942, 43.8481));
        when(geocodingClient.geocode("Yerevan")).thenReturn(new Coordinate(40.1772, 44.5035));

        service.onOrderCreated(ORDER_ID, "Gyumri", "Yerevan");

        ArgumentCaptor<TrackingState> captor = ArgumentCaptor.forClass(TrackingState.class);
        verify(trackingStateRepository).save(captor.capture());
        TrackingState saved = captor.getValue();

        assertThat(saved.getPhase()).isEqualTo(DeliveryPhase.WAITING_FOR_COURIER);
        assertThat(saved.getPickupLat()).isEqualTo(40.7942);
        assertThat(saved.getDestinationLat()).isEqualTo(40.1772);
    }

    @Test
    void onOrderCreated_whenGeocodingFails_stillCreatesStateWithNullCoordinates() {
        when(trackingStateRepository.existsById(ORDER_ID)).thenReturn(false);
        when(geocodingClient.geocode(anyString())).thenReturn(null);

        service.onOrderCreated(ORDER_ID, "Unresolvable address", "Another unresolvable address");

        ArgumentCaptor<TrackingState> captor = ArgumentCaptor.forClass(TrackingState.class);
        verify(trackingStateRepository).save(captor.capture());
        assertThat(captor.getValue().getPickupLat()).isNull();
        assertThat(captor.getValue().getPhase()).isEqualTo(DeliveryPhase.WAITING_FOR_COURIER);
    }

    @Test
    void onOrderCreated_alreadyExists_isIdempotentAndSkipsGeocoding() {
        when(trackingStateRepository.existsById(ORDER_ID)).thenReturn(true);

        service.onOrderCreated(ORDER_ID, "Gyumri", "Yerevan");

        verify(trackingStateRepository, never()).save(any());
        verify(geocodingClient, never()).geocode(anyString());
    }

    @Test
    void onDeliveryStatusChanged_assigned_transitionsToCourierAccepted() {
        TrackingState state = waitingState();
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));
        UUID courierUserId = UUID.randomUUID();

        service.onDeliveryStatusChanged(ORDER_ID, "ASSIGNED", 5L, courierUserId);

        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.COURIER_ACCEPTED);
        assertThat(state.getCourierId()).isEqualTo(5L);
        assertThat(state.getCourierUserId()).isEqualTo(courierUserId);
        verify(kafkaTemplate).send(anyString(), any());
        verify(messagingTemplate).convertAndSend(
                eq("/topic/tracking/order/" + ORDER_ID),
                any(com.example.trackingservice.dto.TrackingStateResponse.class));
    }

    @Test
    void onDeliveryStatusChanged_delivered_isTerminal() {
        TrackingState state = waitingState();
        state.setPhase(DeliveryPhase.ARRIVING);
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        service.onDeliveryStatusChanged(ORDER_ID, "DELIVERED", null, null);

        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.DELIVERED);
    }

    @Test
    void onDeliveryStatusChanged_afterTerminalPhase_doesNotRegress() {
        TrackingState state = waitingState();
        state.setPhase(DeliveryPhase.CANCELLED);
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        service.onDeliveryStatusChanged(ORDER_ID, "ASSIGNED", 5L, UUID.randomUUID());

        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.CANCELLED);
    }

    @Test
    void onDeliveryStatusChanged_unrecognizedStatus_isIgnored() {
        service.onDeliveryStatusChanged(ORDER_ID, "SOME_OTHER_STATUS", null, null);

        verify(trackingStateRepository, never()).findById(any());
    }

    @Test
    void onLocationUpdate_unknownOrder_dropsSilentlyWithoutSaving() {
        when(trackingStateRepository.findById(999L)).thenReturn(Optional.empty());

        service.onLocationUpdate(locationEvent(999L, 40.0, 44.0));

        verify(trackingStateRepository, never()).save(any());
    }

    @Test
    void onLocationUpdate_terminalPhase_isIgnored() {
        TrackingState state = waitingState();
        state.setPhase(DeliveryPhase.DELIVERED);
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        service.onLocationUpdate(locationEvent(ORDER_ID, 40.0, 44.0));

        verify(trackingStateRepository, never()).save(any());
    }

    @Test
    void onLocationUpdate_withinPickupRadius_transitionsToPickedUp() throws Exception {
        TrackingState state = enRouteToPickupState();
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        service.onLocationUpdate(locationEvent(ORDER_ID, 40.7942, 43.8481));

        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.PICKED_UP);
    }

    @Test
    void onLocationUpdate_secondUpdateAfterPickedUp_advancesToInTransit() throws Exception {
        TrackingState state = enRouteToPickupState();
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        service.onLocationUpdate(locationEvent(ORDER_ID, 40.7942, 43.8481));
        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.PICKED_UP);

        service.onLocationUpdate(locationEvent(ORDER_ID, 40.7950, 43.8490));
        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.IN_TRANSIT);
    }

    @Test
    void onLocationUpdate_withinDestinationRadius_transitionsInTransitToArriving() throws Exception {
        TrackingState state = enRouteToPickupState();
        state.setPhase(DeliveryPhase.IN_TRANSIT);
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        service.onLocationUpdate(locationEvent(ORDER_ID, 40.1772, 44.5035));

        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.ARRIVING);
    }

    @Test
    void onLocationUpdate_farFromDestination_staysInTransit() throws Exception {
        TrackingState state = enRouteToPickupState();
        state.setPhase(DeliveryPhase.IN_TRANSIT);
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        service.onLocationUpdate(locationEvent(ORDER_ID, 40.5000, 44.2000));

        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.IN_TRANSIT);
    }

    @Test
    void onLocationUpdate_stuckPastDwellTimeout_autoAdvancesPhase() throws Exception {
        TrackingState state = enRouteToPickupState();
        state.setRouteDurationMin(10.0);
        state.setPhaseChangedAt(LocalDateTime.now().minusMinutes(30));
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        service.onLocationUpdate(locationEvent(ORDER_ID, 40.5000, 44.2000));

        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.PICKED_UP);
    }

    @Test
    void onLocationUpdate_withinDwellWindow_doesNotAutoAdvance() throws Exception {
        TrackingState state = enRouteToPickupState();
        state.setRouteDurationMin(60.0);
        state.setPhaseChangedAt(LocalDateTime.now().minusMinutes(5));
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        service.onLocationUpdate(locationEvent(ORDER_ID, 40.5000, 44.2000));

        assertThat(state.getPhase()).isEqualTo(DeliveryPhase.COURIER_EN_ROUTE_PICKUP);
    }

    @Test
    void getLive_delegatesAccessCheck_andReturnsSnapshot() {
        TrackingState state = waitingState();
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.of(state));

        var response = service.getLive(ORDER_ID);

        verify(trackingAccessGuard).requireAccess(ORDER_ID);
        assertThat(response.orderId()).isEqualTo(ORDER_ID);
        assertThat(response.phase()).isEqualTo("WAITING_FOR_COURIER");
    }

    @Test
    void getLive_noStateForOrder_throwsNotFound() {
        when(trackingStateRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLive(ORDER_ID)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getLive_whenGuardDenies_propagates() {
        doThrow(new AccessDeniedException("nope")).when(trackingAccessGuard).requireAccess(ORDER_ID);

        assertThatThrownBy(() -> service.getLive(ORDER_ID)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getActiveDeliveries_asNonAdmin_throwsAccessDenied() {
        when(currentUser.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> service.getActiveDeliveries(org.springframework.data.domain.PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getStats_asAdmin_returnsAggregateCounts() {
        when(currentUser.isAdmin()).thenReturn(true);
        when(trackingStateRepository.countByPhaseNotIn(any())).thenReturn(3L);
        when(trackingStateRepository.countByCourierUserIdIsNotNullAndPhaseNotIn(any())).thenReturn(2L);
        when(trackingStateRepository.countByPhaseAndPhaseChangedAtAfter(any(), any())).thenReturn(5L);

        var stats = service.getStats();

        assertThat(stats.activeDeliveries()).isEqualTo(3L);
        assertThat(stats.activeCouriers()).isEqualTo(2L);
        assertThat(stats.completedToday()).isEqualTo(5L);
    }

    private TrackingState waitingState() {
        TrackingState state = new TrackingState();
        state.setOrderId(ORDER_ID);
        state.setPhase(DeliveryPhase.WAITING_FOR_COURIER);
        state.setPhaseChangedAt(LocalDateTime.now());
        state.setLastUpdate(LocalDateTime.now());
        return state;
    }

    /** A courier en route to pickup, with a real (small, straight) cached route already set. */
    private TrackingState enRouteToPickupState() throws Exception {
        TrackingState state = waitingState();
        state.setPhase(DeliveryPhase.COURIER_EN_ROUTE_PICKUP);
        state.setPickupLat(40.7942);
        state.setPickupLng(43.8481);
        state.setDestinationLat(40.1772);
        state.setDestinationLng(44.5035);
        state.setRouteDistanceKm(120.0);
        state.setRouteDurationMin(90.0);
        List<double[]> geometry = List.of(
                new double[]{40.7942, 43.8481},
                new double[]{40.5000, 44.2000},
                new double[]{40.1772, 44.5035}
        );
        state.setRouteGeometryJson(objectMapper.writeValueAsString(geometry));
        return state;
    }

    private CourierLocationEvent locationEvent(Long orderId, double lat, double lng) {
        return new CourierLocationEvent(orderId, 5L, UUID.randomUUID(), lat, lng, 90, 40, Instant.now(), "CAR");
    }
}
