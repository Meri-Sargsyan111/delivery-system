package com.example.courierservice.controller;

import com.example.courierservice.courier.CourierStatus;
import com.example.courierservice.dto.AssignmentResponse;
import com.example.courierservice.dto.CourierLocation;
import com.example.courierservice.dto.CourierResponse;
import com.example.courierservice.dto.CreateCourierRequest;
import com.example.courierservice.dto.RateOrderRequest;
import com.example.courierservice.dto.RatingResponse;
import com.example.courierservice.dto.ReserveCourierResponse;
import com.example.courierservice.exception.EntityNotFoundException;
import com.example.courierservice.service.AvatarStorageService;
import com.example.courierservice.service.CourierAssignmentService;
import com.example.courierservice.service.CourierRatingService;
import com.example.courierservice.service.CourierService;
import com.example.courierservice.service.LocationService;
import com.example.courierservice.service.StoredAvatarResource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/courier")
public class CourierController {

    private final CourierService courierService;
    private final LocationService locationService;
    private final CourierAssignmentService courierAssignmentService;
    private final CourierRatingService courierRatingService;
    private final AvatarStorageService avatarStorageService;

    /**
     * Read-only courier summary (id/name/status/photoUrl/rating - no userId or other
     * operational data) is intentionally left open to any authenticated role: the
     * customer-facing orders list needs it to show "Courier X" against an order.
     */
    @GetMapping
    public ResponseEntity<Page<CourierResponse>> listCouriers(
            @RequestParam(required = false) CourierStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("GET /courier - status filter: {}", status);
        return ResponseEntity.ok(courierAssignmentService.listCouriers(status, pageable));
    }

    @PreAuthorize("hasRole('COURIER')")
    @GetMapping("/me")
    public ResponseEntity<CourierResponse> getMyCourier() {
        log.info("GET /courier/me");
        return ResponseEntity.ok(courierAssignmentService.getMyCourier());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<CourierResponse> createCourier(@Valid @RequestBody CreateCourierRequest request) {
        log.info("POST /courier - creating courier");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courierAssignmentService.createCourier(request));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('COURIER')")
    @PutMapping("/{courierId}/status")
    public ResponseEntity<CourierResponse> changeStatus(
            @PathVariable Long courierId, @RequestParam CourierStatus status) {
        log.info("PUT /courier/{}/status?status={}", courierId, status);
        return ResponseEntity.ok(courierAssignmentService.changeStatus(courierId, status));
    }

    @PreAuthorize("hasRole('CUSTOMER') or hasRole('ADMIN')")
    @PostMapping("/rating/{orderId}")
    public ResponseEntity<RatingResponse> rateOrder(
            @PathVariable Long orderId, @Valid @RequestBody RateOrderRequest request) {
        log.info("POST /courier/rating/{}", orderId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(courierRatingService.rateOrder(orderId, request));
    }

    @GetMapping("/available")
    public ResponseEntity<List<CourierResponse>> getAvailableCouriers() {
        log.info("GET /courier/available");
        return ResponseEntity.ok(courierAssignmentService.getAvailableCouriers());
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('COURIER')")
    @GetMapping("/assignment/{orderId}")
    public ResponseEntity<AssignmentResponse> getAssignment(@PathVariable Long orderId) {
        log.info("GET /courier/assignment/{}", orderId);
        return ResponseEntity.ok(courierAssignmentService.getAssignment(orderId));
    }

    /**
     * Stays public/unauthenticated: order-service's CourierServiceClient calls this
     * synchronously during assignment and carries no credential today (pre-existing,
     * documented gap - see SecurityConfig). Now returns the courier's linked userId
     * so order-service can record a locally-checkable ownership link.
     */
    @PutMapping("/{courierId}/reserve/{orderId}")
    public ResponseEntity<ReserveCourierResponse> reserveCourier(
            @PathVariable Long courierId, @PathVariable Long orderId) {
        log.info("PUT /courier/{}/reserve/{} - reserving courier for order", courierId, orderId);
        UUID courierUserId = courierAssignmentService.reserveCourier(courierId, orderId);
        return ResponseEntity.ok(new ReserveCourierResponse(courierId, courierUserId));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('COURIER')")
    @PutMapping("/start/{orderId}")
    public ResponseEntity<String> startDelivery(@PathVariable Long orderId) {
        log.info("PUT /courier/start/{} - starting delivery", orderId);
        return ResponseEntity.ok(courierService.startDelivery(orderId));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('COURIER')")
    @PutMapping("/deliver/{orderId}")
    public ResponseEntity<String> markAsDelivered(@PathVariable Long orderId) {
        log.info("PUT /courier/deliver/{} - marking as delivered", orderId);
        return ResponseEntity.ok(courierService.markAsDelivered(orderId));
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('COURIER')")
    @PostMapping("/location")
    public ResponseEntity<Void> sendLocation(@RequestBody CourierLocation location) {
        locationService.sendLocation(location);
        return ResponseEntity.ok().build();
    }

    /**
     * Self-service avatar upload: always resolves the target courier from the caller's
     * own JWT (see CourierAssignmentService.uploadMyAvatar), never from a client-supplied id.
     */
    @PreAuthorize("hasRole('COURIER')")
    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourierResponse> uploadMyAvatar(@RequestParam("file") MultipartFile file) {
        log.info("POST /courier/me/avatar");
        return ResponseEntity.ok(courierAssignmentService.uploadMyAvatar(file));
    }

    /**
     * Explicit admin override, distinct from the courier's own upload path: an admin
     * cannot overwrite a courier's avatar via /me/avatar, only via this dedicated endpoint.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping(value = "/{courierId}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CourierResponse> uploadAvatarForCourier(
            @PathVariable Long courierId, @RequestParam("file") MultipartFile file) {
        log.info("PUT /courier/{}/avatar - admin override", courierId);
        return ResponseEntity.ok(courierAssignmentService.uploadAvatarForCourier(courierId, file));
    }

    /**
     * Read-only, left open to any authenticated role - matches {@link #listCouriers}'s
     * openness, since the same customer-facing orders list needs to render this image.
     */
    @GetMapping("/{courierId}/avatar")
    public ResponseEntity<Resource> getAvatar(@PathVariable Long courierId) {
        StoredAvatarResource avatar = avatarStorageService.load(courierId)
                .orElseThrow(() -> new EntityNotFoundException("No avatar found for courier " + courierId));
        return ResponseEntity.ok().contentType(avatar.mediaType()).body(avatar.resource());
    }
}