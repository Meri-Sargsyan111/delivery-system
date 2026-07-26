package com.example.trackingservice.security;

import com.example.trackingservice.client.OrderServiceClient;
import com.example.trackingservice.client.RemoteOrderView;
import com.example.trackingservice.entity.OrderOwnership;
import com.example.trackingservice.repository.OrderOwnershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

/**
 * ADMIN sees any order's tracking; CUSTOMER only their own order; COURIER only an order
 * assigned to them, per the local OrderOwnership projection built from Kafka events (see
 * OrderOwnership). That projection is eventually consistent - a customer can legitimately
 * open tracking before the new-orders event for their own order has been consumed yet, or
 * an order-created event can be missed/delayed entirely - so a local cache miss falls
 * back to a synchronous read of order-service's own ownership (see OrderServiceClient,
 * same pattern courier-service already uses for start/deliver gating) before finally
 * denying. A confirmed remote match is written back into the local projection so
 * subsequent reads for the same order don't need the fallback.
 *
 * Extracted from TrackingServiceImpl so both the existing status-timeline endpoints and
 * the new live-tracking endpoints/WebSocket enforce the identical rule from one place.
 */
@Component
@RequiredArgsConstructor
public class TrackingAccessGuard {

    private final OrderOwnershipRepository orderOwnershipRepository;
    private final OrderServiceClient orderServiceClient;
    private final CurrentUser currentUser;

    public void requireAccess(Long orderId) {
        if (currentUser.isAdmin()) {
            return;
        }

        OrderOwnership ownership = orderOwnershipRepository.findById(orderId).orElse(null);
        if (isOwner(ownership)) {
            return;
        }

        RemoteOrderView remote = orderServiceClient.getOrder(orderId);
        if (remote != null && isOwner(remote)) {
            backfillOwnership(remote);
            return;
        }

        throw new AccessDeniedException("Not authorized to access tracking for order " + orderId);
    }

    /** Same rule as requireAccess, usable from contexts that already resolved identity (e.g. the WebSocket interceptor). */
    public boolean isOwnerOrAdmin(Long orderId, java.util.UUID userId, String role) {
        if ("ADMIN".equals(role)) {
            return true;
        }
        OrderOwnership ownership = orderOwnershipRepository.findById(orderId).orElse(null);
        if (isOwner(ownership, userId, role)) {
            return true;
        }
        RemoteOrderView remote = orderServiceClient.getOrder(orderId);
        if (remote != null && isOwner(remote, userId, role)) {
            backfillOwnership(remote);
            return true;
        }
        return false;
    }

    private boolean isOwner(OrderOwnership ownership) {
        return isOwner(ownership, currentUser.getUserId(), currentRole());
    }

    private boolean isOwner(RemoteOrderView remote) {
        return isOwner(remote, currentUser.getUserId(), currentRole());
    }

    private boolean isOwner(OrderOwnership ownership, java.util.UUID userId, String role) {
        if (ownership == null) {
            return false;
        }
        if ("CUSTOMER".equals(role) && userId.equals(ownership.getCustomerUserId())) {
            return true;
        }
        return "COURIER".equals(role) && userId.equals(ownership.getCourierUserId());
    }

    private boolean isOwner(RemoteOrderView remote, java.util.UUID userId, String role) {
        if ("CUSTOMER".equals(role) && userId.equals(remote.getCustomerUserId())) {
            return true;
        }
        return "COURIER".equals(role) && userId.equals(remote.getCourierUserId());
    }

    private String currentRole() {
        if (currentUser.isCustomer()) {
            return "CUSTOMER";
        }
        if (currentUser.isCourier()) {
            return "COURIER";
        }
        return "";
    }

    private void backfillOwnership(RemoteOrderView remote) {
        OrderOwnership ownership = orderOwnershipRepository.findById(remote.getId())
                .orElseGet(() -> new OrderOwnership(remote.getId(), null, null));
        ownership.setCustomerUserId(remote.getCustomerUserId());
        ownership.setCourierUserId(remote.getCourierUserId());
        orderOwnershipRepository.save(ownership);
    }
}
