package com.example.trackingservice.service;

import com.example.trackingservice.entity.TrackingEvent;
import com.example.trackingservice.repository.TrackingEventRepository;
import com.example.trackingservice.service.impl.TrackingServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackingServiceImplTest {

    @Mock private TrackingEventRepository trackingEventRepository;

    @InjectMocks private TrackingServiceImpl trackingService;

    private TrackingEvent event(Long id, Long orderId, String status) {
        return new TrackingEvent(id, orderId, "System", status, LocalDateTime.now());
    }


    @Test
    void getTracking_whenEventsExist_returnsAllEventsForOrder() {
        List<TrackingEvent> events = List.of(
                event(1L, 42L, "IN_PROGRESS"),
                event(2L, 42L, "DELIVERED")
        );
        when(trackingEventRepository.findByOrderId(42L)).thenReturn(events);

        List<TrackingEvent> result = trackingService.getTracking(42L);

        assertThat(result).hasSize(2);
        assertThat(result).isEqualTo(events);
        verify(trackingEventRepository).findByOrderId(42L);
    }

    @Test
    void getTracking_whenNoEventsFound_returnsEmptyListWithoutThrowing() {
        when(trackingEventRepository.findByOrderId(99L)).thenReturn(List.of());

        List<TrackingEvent> result = trackingService.getTracking(99L);

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
    void getAllEvents_returnsPageFromRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<TrackingEvent> expected = new PageImpl<>(List.of(
                event(1L, 10L, "IN_PROGRESS"),
                event(2L, 11L, "DELIVERED")
        ));
        when(trackingEventRepository.findAll(pageable)).thenReturn(expected);

        Page<TrackingEvent> result = trackingService.getAllEvents(pageable);

        assertThat(result).isEqualTo(expected);
        assertThat(result.getTotalElements()).isEqualTo(2);
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