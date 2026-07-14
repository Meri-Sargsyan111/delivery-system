package com.example.courierservice.service.impl;

import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.dto.AssignmentResponse;
import com.example.courierservice.dto.CourierResponse;
import com.example.courierservice.dto.CreateCourierRequest;
import com.example.courierservice.entity.Courier;
import com.example.courierservice.entity.CourierAssignment;
import com.example.courierservice.event.DeliveryUpdateEvent;
import com.example.courierservice.exception.CourierNotAvailableException;
import com.example.courierservice.exception.EntityNotFoundException;
import com.example.courierservice.repository.CourierAssignmentRepository;
import com.example.courierservice.repository.CourierRatingRepository;
import com.example.courierservice.repository.CourierRepository;
import com.example.courierservice.security.CurrentUser;
import com.example.courierservice.service.AvatarStorageService;
import com.example.courierservice.service.CourierAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
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

    @Override
    public Page<CourierResponse> listCouriers(CourierStatus status, Pageable pageable) {
        Page<Courier> couriers = status != null
                ? courierRepository.findByStatus(status, pageable)
                : courierRepository.findAll(pageable);

        return couriers.map(this::toResponse);
    }

    @Override
    public List<CourierResponse> getAvailableCouriers() {
        return courierRepository.findByStatus(CourierStatus.AVAILABLE).stream().map(this::toResponse).toList();
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

        kafkaTemplate.send("delivery-updates",
                new DeliveryUpdateEvent(orderId, courier.getName(), "ASSIGNED", courier.getUserId()));

        return courier.getUserId();
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
        Courier courier = new Courier(null, request.getName(), CourierStatus.AVAILABLE,
                request.getPhotoUrl(), request.getUserId());
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

        Courier courier = new Courier(null, name, CourierStatus.AVAILABLE, null, userId);
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
        Double averageRating = courierRatingRepository.findAverageRatingByCourierId(courier.getId());
        long ratingCount = courierRatingRepository.countByCourierId(courier.getId());
        return new CourierResponse(courier.getId(), courier.getName(), courier.getStatus(),
                averageRating, (int) ratingCount, courier.getPhotoUrl());
    }
}