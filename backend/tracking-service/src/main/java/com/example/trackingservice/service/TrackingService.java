package com.example.trackingservice.service;

import com.example.trackingservice.dto.TrackingEventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for querying courier tracking events.
 *
 * <p>Provides access to the history of delivery status updates associated
 * with a specific order, as well as paginated access to the full event log.
 */
public interface TrackingService {

    /**
     * Returns all tracking events recorded for the given order, in the order
     * they were persisted.
     *
     * @param orderId the unique identifier of the order to look up
     * @return a list of {@link TrackingEventResponse} entries for that order;
     *         empty if no events have been recorded yet
     * @throws org.springframework.security.access.AccessDeniedException if the caller is
     *         not ADMIN, the owning customer, or the assigned courier for this order
     */
    List<TrackingEventResponse> getTracking(Long orderId);

    /**
     * Returns a paginated view of every tracking event across all orders. ADMIN only.
     *
     * @param pageable pagination and sorting parameters
     * @return a {@link Page} of {@link TrackingEventResponse}
     */
    Page<TrackingEventResponse> getAllEvents(Pageable pageable);
}
