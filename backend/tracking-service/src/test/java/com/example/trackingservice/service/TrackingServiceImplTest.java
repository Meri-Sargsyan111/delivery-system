package com.example.trackingservice.service;

import com.example.trackingservice.dto.TrackingEventResponse;
import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.mapper.TrackingEventMapper;
import com.example.trackingservice.repository.TrackingEventRepository;
import com.example.trackingservice.security.CurrentUser;
import com.example.trackingservice.security.TrackingAccessGuard;
import com.example.trackingservice.service.impl.TrackingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Access-check scenarios (admin/owner/fallback) moved to TrackingAccessGuardTest -
 * TrackingServiceImpl now just delegates to TrackingAccessGuard.requireAccess, so this
 * class only needs to verify that delegation happens and that a denial propagates.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackingServiceImplTest {

    @Mock private TrackingEventRepository trackingEventRepository;
    @Mock private CurrentUser currentUser;
    @Mock private TrackingAccessGuard trackingAccessGuard;
    @Spy private TrackingEventMapper trackingEventMapper = new TrackingEventMapper();

    @InjectMocks private TrackingServiceImpl trackingService;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.isAdmin()).thenReturn(true);
    }

    private TrackingEvent event(Long id, Long orderId, String status) {
        return new TrackingEvent(id, orderId, "System", status, LocalDateTime.now());
    }

    @Test
    void getTracking_delegatesAccessCheckToGuard_andReturnsEventsWhenAllowed() {
        when(trackingEventRepository.findByOrderId(42L)).thenReturn(List.of(event(1L, 42L, "IN_PROGRESS")));

        List<TrackingEventResponse> result = trackingService.getTracking(42L);

        assertThat(result).hasSize(1);
        verify(trackingAccessGuard).requireAccess(42L);
    }

    @Test
    void getTracking_whenGuardDenies_propagatesAccessDenied() {
        doThrow(new AccessDeniedException("Not authorized to access tracking for order 42"))
                .when(trackingAccessGuard).requireAccess(42L);

        assertThatThrownBy(() -> trackingService.getTracking(42L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getTracking_whenNoEventsFound_returnsEmptyListWithoutThrowing() {
        when(trackingEventRepository.findByOrderId(99L)).thenReturn(List.of());

        List<TrackingEventResponse> result = trackingService.getTracking(99L);

        assertThat(result).isEmpty();
        verify(trackingEventRepository).findByOrderId(99L);
    }

    @Test
    void getTracking_passesCorrectOrderIdToRepository() {
        Long orderId = 7L;
        when(trackingEventRepository.findByOrderId(orderId))
                .thenReturn(List.of(event(1L, orderId, "IN_PROGRESS")));

        trackingService.getTracking(orderId);

        verify(trackingEventRepository).findByOrderId(orderId);
    }

    @Test
    void getTracking_whenRepositoryThrows_propagatesException() {
        when(trackingEventRepository.findByOrderId(42L))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> trackingService.getTracking(42L))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");
    }

    @Test
    void getAllEvents_asNonAdmin_throwsAccessDenied() {
        when(currentUser.isAdmin()).thenReturn(false);

        assertThatThrownBy(() -> trackingService.getAllEvents(PageRequest.of(0, 20)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getAllEvents_returnsPageFromRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<TrackingEvent> expected = new PageImpl<>(List.of(
                event(1L, 10L, "IN_PROGRESS"),
                event(2L, 11L, "DELIVERED")
        ));
        when(trackingEventRepository.findAll(pageable)).thenReturn(expected);

        Page<TrackingEventResponse> result = trackingService.getAllEvents(pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(expected.getContent().get(0).getId());
        verify(trackingEventRepository).findAll(pageable);
    }

    @Test
    void getAllEvents_passesPageableToRepository() {
        Pageable pageable = PageRequest.of(2, 5);
        when(trackingEventRepository.findAll(pageable)).thenReturn(Page.empty());

        trackingService.getAllEvents(pageable);

        verify(trackingEventRepository).findAll(pageable);
    }

    @Test
    void getAllEvents_whenRepositoryThrows_propagatesException() {
        Pageable pageable = PageRequest.of(0, 20);
        when(trackingEventRepository.findAll(pageable))
                .thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> trackingService.getAllEvents(pageable))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");
    }
}
