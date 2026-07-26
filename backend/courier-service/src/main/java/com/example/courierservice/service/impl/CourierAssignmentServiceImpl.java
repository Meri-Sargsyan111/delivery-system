package com.example.courierservice.service.impl;

import com.example.courierservice.client.AuthServiceClient;
import com.example.courierservice.client.OrderServiceClient;
import com.example.courierservice.client.UserContactLookupResult;
import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.courier.VehicleType;
import com.example.courierservice.dto.AssignmentResponse;
import com.example.courierservice.dto.CourierContactCardResponse;
import com.example.courierservice.dto.CourierResponse;
import com.example.courierservice.dto.CreateCourierRequest;
import com.example.courierservice.entity.Courier;
import com.example.courierservice.entity.CourierAssignment;
import com.example.courierservice.event.DeliveryUpdateEvent;
import com.example.courierservice.exception.CourierNotAvailableException;
import com.example.courierservice.exception.EntityNotFoundException;
import com.example.courierservice.exception.InvalidOrderStateException;
import com.example.courierservice.repository.CourierAssignmentRepository;
import com.example.courierservice.repository.CourierRatingRepository;
import com.example.courierservice.repository.CourierRepository;
import com.example.courierservice.security.CurrentUser;
import com.example.courierservice.service.AvatarStorageService;
import com.example.courierservice.service.CourierAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierAssignmentServiceImpl implements CourierAssignmentService {

    private final CourierRepository courierRepository;
    private final CourierAssignmentRepository courierAssignmentRepository;
    private final CourierRatingRepository courierRatingRepository;
    private final KafkaTemplate<String, DeliveryUpdateEvent> kafkaTemplate;
    private final CurrentUser currentUser;
    private final AvatarStorageService avatarStorageService;
    private final OrderServiceClient orderServiceClient;
    private final AuthServiceClient authServiceClient;
    private final TaskScheduler taskScheduler;

    @Value("${courier.assignment.offer-timeout-seconds:20}")
    private long offerTimeoutSeconds;

    @Override
    public Page<CourierResponse> listCouriers(CourierStatus status, Pageable pageable) {
        Page<Courier> couriers = status != null
                ? courierRepository.findByStatus(status, pageable)
                : courierRepository.findAll(pageable);

        Map<UUID, UserContactLookupResult> contacts = fetchContacts(couriers.getContent());
        return couriers.map(courier -> toResponse(courier, contacts));
    }

    @Override
    public List<CourierResponse> getAvailableCouriers() {
        List<Courier> couriers = courierRepository.findByStatus(CourierStatus.AVAILABLE);
        Map<UUID, UserContactLookupResult> contacts = fetchContacts(couriers);
        return couriers.stream().map(courier -> toResponse(courier, contacts)).toList();
    }

    /** One auth-service round trip for an entire roster instead of one per courier - see AuthServiceClient. */
    private Map<UUID, UserContactLookupResult> fetchContacts(List<Courier> couriers) {
        List<UUID> userIds = couriers.stream().map(Courier::getUserId).filter(Objects::nonNull).toList();
        return authServiceClient.getUserContacts(userIds);
    }

    @Override
    public CourierResponse getMyCourier() {
        Courier courier = courierRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("No courier record linked to this account"));
        return toResponse(courier);
    }

    @Override
    public AssignmentResponse getAssignment(Long orderId) {
        CourierAssignment assignment = courierAssignmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("No assignment found for order: " + orderId));

        Courier courier = courierRepository.findById(assignment.getCourierId())
                .orElseThrow(() -> new EntityNotFoundException("Courier not found with id: " + assignment.getCourierId()));

        return new AssignmentResponse(assignment.getOrderId(), assignment.getCourierId(),
                courier.getName(), assignment.getAssignedAt());
    }

    @Override
    public UUID reserveCourier(Long courierId, Long orderId) {
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new EntityNotFoundException("Courier not found with id: " + courierId));

        if (courier.getStatus() != CourierStatus.AVAILABLE) {
            throw new CourierNotAvailableException(
                    "Courier " + courierId + " is not available: current status is " + courier.getStatus());
        }

        courier.setStatus(CourierStatus.BUSY);
        courierRepository.save(courier);

        courierAssignmentRepository.save(new CourierAssignment(null, orderId, courierId, LocalDateTime.now()));
        log.info("Courier {} reserved for order {}", courierId, orderId);

        try {
            kafkaTemplate.send("delivery-updates",
                    new DeliveryUpdateEvent(orderId, courier.getName(), "ASSIGNED", courier.getUserId()));
        } catch (Exception e) {
            log.error("Courier {} was reserved for order {} but publishing the ASSIGNED delivery update failed",
                    courierId, orderId, e);
        }

        scheduleOfferTimeout(orderId, courierId);

        return courier.getUserId();
    }

    /**
     * One-shot delayed check, not a recurring sweep: cheap and simple for a single-instance
     * deployment, at the cost of a scheduled check being lost if this process restarts
     * mid-window (worst case: that one order sits ASSIGNED a bit longer than the configured
     * timeout until an admin notices or the courier eventually responds - not a correctness
     * issue, since assignment state itself always lives in the database, not in this task).
     */
    private void scheduleOfferTimeout(Long orderId, Long courierId) {
        taskScheduler.schedule(
                () -> checkOfferTimeout(orderId, courierId),
                Instant.now().plusSeconds(offerTimeoutSeconds));
    }

    /**
     * Runs on a background scheduler thread with no authenticated caller - same trust model
     * as the Kafka consumers and the Live Tracking simulator elsewhere in this codebase (see
     * CurrentUser.isAuthenticated()'s javadoc): courierId here was captured internally at
     * reservation time, never supplied by an untrusted caller, so no ownership check is
     * needed before acting on it.
     */
    private void checkOfferTimeout(Long orderId, Long courierId) {
        try {
            Optional<CourierAssignment> assignment = courierAssignmentRepository.findByOrderId(orderId);
            if (assignment.isEmpty() || !assignment.get().getCourierId().equals(courierId)) {
                return;
            }

            Courier courier = courierRepository.findById(courierId).orElse(null);
            if (courier == null) {
                log.warn("Offer-timeout sweep for order {} found no courier {} - assignment left as-is", orderId, courierId);
                return;
            }

            if (!isStillAwaitingResponse(orderId)) {
                log.info("Offer-timeout check for order {} skipped - courier already responded (order has moved " +
                        "past ASSIGNED)", orderId);
                return;
            }

            log.info("Courier {} did not respond to order {} within {}s - reverting to admin queue",
                    courierId, orderId, offerTimeoutSeconds);
            releaseAssignment(assignment.get(), courier, "OFFER_EXPIRED");
        } catch (Exception e) {
            log.error("Offer-timeout check failed for order {}, courier {} - assignment left as-is, " +
                    "will need manual admin attention", orderId, courierId, e);
        }
    }

    @Override
    public void rejectAssignment(Long orderId) {
        Courier courier = courierRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("No courier record linked to this account"));

        CourierAssignment assignment = courierAssignmentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new EntityNotFoundException("No assignment found for order: " + orderId));

        if (!assignment.getCourierId().equals(courier.getId())) {
            throw new AccessDeniedException("Not authorized to reject the assignment for order " + orderId);
        }

        if (!isStillAwaitingResponse(orderId)) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " is no longer awaiting a response - it may already have been accepted");
        }

        log.info("Courier {} rejected assignment for order {}", courier.getId(), orderId);
        releaseAssignment(assignment, courier, "REJECTED");
    }

    /**
     * Guards both reject and the offer-timeout sweep against a race with accept: courier
     * accepting (see CourierServiceImpl.startDelivery) moves the order straight to
     * IN_PROGRESS without touching this CourierAssignment row at all, so the row's mere
     * presence isn't enough to tell "still pending" apart from "already accepted, delivery
     * under way". order-service's own OrderServiceImpl.unassignOrder has this same guard
     * server-side (belt and suspenders), but without checking here too, courier-service's
     * OWN state (courier freed back to AVAILABLE, assignment row deleted) would still get
     * corrupted for an actively-delivering courier even though the order itself stayed
     * correctly untouched.
     */
    private boolean isStillAwaitingResponse(Long orderId) {
        return "ASSIGNED".equals(orderServiceClient.getOrder(orderId).getStatus());
    }

    /**
     * Shared by both a courier's explicit reject and the offer-timeout sweep - the outcome
     * is identical either way (see CourierAssignmentService.rejectAssignment): the courier
     * is freed, the assignment record is removed, order-service reverts the order to
     * CREATED (never auto-reassigned), and a delivery update notifies admin (and anyone
     * else listening on the shared notification broadcast).
     */
    private void releaseAssignment(CourierAssignment assignment, Courier courier, String status) {
        orderServiceClient.unassignOrder(assignment.getOrderId(), courier.getId());

        courier.setStatus(CourierStatus.AVAILABLE);
        courierRepository.save(courier);

        courierAssignmentRepository.delete(assignment);

        try {
            kafkaTemplate.send("delivery-updates",
                    new DeliveryUpdateEvent(assignment.getOrderId(), courier.getName(), status, courier.getUserId()));
        } catch (Exception e) {
            log.error("Order {} was unassigned from courier {} (status={}) but publishing the delivery update failed",
                    assignment.getOrderId(), courier.getId(), status, e);
        }
    }

    @Override
    public void releaseCourierForOrder(Long orderId) {
        Optional<CourierAssignment> assignment = courierAssignmentRepository.findByOrderId(orderId);
        if (assignment.isEmpty()) {
            log.debug("No assignment found for order {}, nothing to release", orderId);
            return;
        }

        courierRepository.findById(assignment.get().getCourierId()).ifPresentOrElse(courier -> {
            courier.setStatus(CourierStatus.AVAILABLE);
            courierRepository.save(courier);
            log.info("Courier {} released back to AVAILABLE after order {}", courier.getId(), orderId);
        }, () -> log.warn("Assignment for order {} references missing courier {}",
                orderId, assignment.get().getCourierId()));
    }

    @Override
    public CourierResponse createCourier(CreateCourierRequest request) {
        VehicleType vehicleType = request.getVehicleType() != null ? request.getVehicleType() : VehicleType.CAR;
        Courier courier = new Courier(null, request.getName(), CourierStatus.AVAILABLE,
                request.getPhotoUrl(), request.getUserId(), vehicleType);
        courierRepository.save(courier);
        log.info("Courier {} created with status AVAILABLE", courier.getId());
        return toResponse(courier);
    }

    @Override
    public CourierResponse changeStatus(Long courierId, CourierStatus targetStatus) {
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new EntityNotFoundException("Courier not found with id: " + courierId));

        requireAdminOrOwningCourier(courier);

        if (targetStatus == CourierStatus.BUSY) {
            throw new IllegalArgumentException("BUSY is a system-managed status and cannot be set manually");
        }

        if (courier.getStatus() == CourierStatus.BUSY) {
            throw new CourierNotAvailableException(
                    "Courier " + courierId + " is currently on an active delivery and cannot be changed manually");
        }

        courier.setStatus(targetStatus);
        courierRepository.save(courier);
        log.info("Courier {} status manually changed to {}", courierId, targetStatus);
        return toResponse(courier);
    }

    /**
     * ADMIN may change any courier's status; a COURIER may only change their own
     * (courier.userId == JWT sub) - never matched by name, per ownership rules.
     */
    private void requireAdminOrOwningCourier(Courier courier) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (currentUser.isCourier() && currentUser.getUserId().equals(courier.getUserId())) {
            return;
        }
        throw new AccessDeniedException("Not authorized to modify courier " + courier.getId());
    }

    @Override
    public void registerCourierAccount(UUID userId, String name) {
        if (courierRepository.findByUserId(userId).isPresent()) {
            log.info("Courier already linked to user {}, skipping duplicate registration event", userId);
            return;
        }

        Courier courier = new Courier(null, name, CourierStatus.AVAILABLE, null, userId, VehicleType.CAR);
        courierRepository.save(courier);
        log.info("Courier profile auto-created for newly registered user {}", userId);
    }

    @Override
    public CourierResponse uploadMyAvatar(MultipartFile file) {
        Courier courier = courierRepository.findByUserId(currentUser.getUserId())
                .orElseThrow(() -> new EntityNotFoundException("No courier record linked to this account"));
        return storeAvatarAndRespond(courier, file);
    }

    @Override
    public CourierResponse uploadAvatarForCourier(Long courierId, MultipartFile file) {
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new EntityNotFoundException("Courier not found with id: " + courierId));
        return storeAvatarAndRespond(courier, file);
    }

    private CourierResponse storeAvatarAndRespond(Courier courier, MultipartFile file) {
        String avatarUrl = avatarStorageService.store(courier.getId(), file);
        courier.setPhotoUrl(avatarUrl);
        courierRepository.save(courier);
        log.info("Avatar updated for courier {}", courier.getId());
        return toResponse(courier);
    }

    private CourierResponse toResponse(Courier courier) {
        Map<UUID, UserContactLookupResult> contacts = fetchContacts(List.of(courier));
        return toResponse(courier, contacts);
    }

    private CourierResponse toResponse(Courier courier, Map<UUID, UserContactLookupResult> contactsByUserId) {
        Double averageRating = courierRatingRepository.findAverageRatingByCourierId(courier.getId());
        long ratingCount = courierRatingRepository.countByCourierId(courier.getId());
        VehicleType vehicleType = courier.getVehicleType() != null ? courier.getVehicleType() : VehicleType.CAR;
        UserContactLookupResult contact = courier.getUserId() != null ? contactsByUserId.get(courier.getUserId()) : null;
        String phone = contact != null ? contact.phoneNumber() : null;
        return new CourierResponse(courier.getId(), courier.getName(), courier.getStatus(),
                averageRating, (int) ratingCount, courier.getPhotoUrl(), vehicleType,
                phone, courier.getCompletedDeliveries());
    }

    @Override
    public void incrementCompletedDeliveries(Long courierId) {
        courierRepository.findById(courierId).ifPresentOrElse(courier -> {
            courier.setCompletedDeliveries(courier.getCompletedDeliveries() + 1);
            courierRepository.save(courier);
        }, () -> log.warn("Cannot increment completed-deliveries count: courier {} not found", courierId));
    }

    @Override
    public CourierContactCardResponse getContactCard(Long courierId) {
        Courier courier = courierRepository.findById(courierId)
                .orElseThrow(() -> new EntityNotFoundException("Courier not found with id: " + courierId));

        Double averageRating = courierRatingRepository.findAverageRatingByCourierId(courierId);
        UserContactLookupResult contact = authServiceClient.getUserContact(courier.getUserId());

        String fullName = contact != null ? contact.fullName() : courier.getName();
        String phoneNumber = contact != null ? contact.phoneNumber() : null;

        return new CourierContactCardResponse(
                fullName, phoneNumber, averageRating, courier.getCompletedDeliveries());
    }
}