package com.example.courierservice.service;

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
import com.example.courierservice.service.impl.CourierAssignmentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourierAssignmentServiceImplTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID COURIER_USER_ID = UUID.randomUUID();
    private static final UUID OTHER_COURIER_USER_ID = UUID.randomUUID();

    @Mock private CourierRepository courierRepository;
    @Mock private CourierAssignmentRepository courierAssignmentRepository;
    @Mock private CourierRatingRepository courierRatingRepository;
    @Mock private KafkaTemplate<String, DeliveryUpdateEvent> kafkaTemplate;
    @Mock private CurrentUser currentUser;

    @InjectMocks private CourierAssignmentServiceImpl courierAssignmentService;

    @BeforeEach
    void setUp() {
        asAdmin();
    }

    private void asAdmin() {
        lenient().when(currentUser.isAdmin()).thenReturn(true);
        lenient().when(currentUser.isCourier()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(ADMIN_ID);
    }

    private void asCourier(UUID userId) {
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.isCourier()).thenReturn(true);
        lenient().when(currentUser.getUserId()).thenReturn(userId);
    }

    @Test
    void reserveCourier_whenAvailable_marksBusyPersistsAssignmentAndPublishesEventWithCourierUserId() {
        Courier courier = new Courier(5L, "Alice Johnson", CourierStatus.AVAILABLE, null, COURIER_USER_ID);
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));

        UUID result = courierAssignmentService.reserveCourier(5L, 10L);

        assertThat(result).isEqualTo(COURIER_USER_ID);
        assertThat(courier.getStatus()).isEqualTo(CourierStatus.BUSY);
        verify(courierRepository).save(courier);

        ArgumentCaptor<CourierAssignment> assignmentCaptor = ArgumentCaptor.forClass(CourierAssignment.class);
        verify(courierAssignmentRepository).save(assignmentCaptor.capture());
        assertThat(assignmentCaptor.getValue().getOrderId()).isEqualTo(10L);
        assertThat(assignmentCaptor.getValue().getCourierId()).isEqualTo(5L);

        ArgumentCaptor<DeliveryUpdateEvent> eventCaptor = ArgumentCaptor.forClass(DeliveryUpdateEvent.class);
        verify(kafkaTemplate).send(eq("delivery-updates"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().getStatus()).isEqualTo("ASSIGNED");
        assertThat(eventCaptor.getValue().getCourierName()).isEqualTo("Alice Johnson");
        assertThat(eventCaptor.getValue().getCourierUserId()).isEqualTo(COURIER_USER_ID);
    }

    @Test
    void reserveCourier_whenBusy_throwsCourierNotAvailableAndDoesNotPersist() {
        Courier courier = new Courier(5L, "Alice Johnson", CourierStatus.BUSY, null, COURIER_USER_ID);
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> courierAssignmentService.reserveCourier(5L, 10L))
                .isInstanceOf(CourierNotAvailableException.class);

        verify(courierRepository, never()).save(any());
        verify(courierAssignmentRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any());
    }

    @Test
    void reserveCourier_whenOffline_throwsCourierNotAvailable() {
        Courier courier = new Courier(5L, "David Petrov", CourierStatus.OFFLINE, null, null);
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> courierAssignmentService.reserveCourier(5L, 10L))
                .isInstanceOf(CourierNotAvailableException.class);

        verify(courierAssignmentRepository, never()).save(any());
    }

    @Test
    void reserveCourier_whenCourierNotFound_throwsEntityNotFoundException() {
        when(courierRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierAssignmentService.reserveCourier(99L, 10L))
                .isInstanceOf(EntityNotFoundException.class);

        verify(courierAssignmentRepository, never()).save(any());
    }

    @Test
    void releaseCourierForOrder_whenAssignmentExists_setsCourierAvailable() {
        CourierAssignment assignment = new CourierAssignment(1L, 10L, 5L, LocalDateTime.now());
        Courier courier = new Courier(5L, "Alice Johnson", CourierStatus.BUSY, null, COURIER_USER_ID);
        when(courierAssignmentRepository.findByOrderId(10L)).thenReturn(Optional.of(assignment));
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));

        courierAssignmentService.releaseCourierForOrder(10L);

        assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
        verify(courierRepository).save(courier);
    }

    @Test
    void releaseCourierForOrder_calledFromUnauthenticatedKafkaConsumerContext_stillWorks() {

        CourierAssignment assignment = new CourierAssignment(1L, 10L, 5L, LocalDateTime.now());
        Courier courier = new Courier(5L, "Alice Johnson", CourierStatus.BUSY, null, COURIER_USER_ID);
        when(courierAssignmentRepository.findByOrderId(10L)).thenReturn(Optional.of(assignment));
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));

        courierAssignmentService.releaseCourierForOrder(10L);

        assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
    }

    @Test
    void releaseCourierForOrder_whenNoAssignmentExists_isNoOp() {
        when(courierAssignmentRepository.findByOrderId(10L)).thenReturn(Optional.empty());

        courierAssignmentService.releaseCourierForOrder(10L);

        verify(courierRepository, never()).save(any());
    }

    @Test
    void listCouriers_withStatusFilter_delegatesToRepositoryFindByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Courier available = new Courier(1L, "Alice Johnson", CourierStatus.AVAILABLE, null, null);
        when(courierRepository.findByStatus(CourierStatus.AVAILABLE, pageable))
                .thenReturn(new PageImpl<>(List.of(available)));

        Page<CourierResponse> result = courierAssignmentService.listCouriers(CourierStatus.AVAILABLE, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(1L);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo(CourierStatus.AVAILABLE);
    }

    @Test
    void listCouriers_withoutStatusFilter_returnsAllCouriers() {
        Pageable pageable = PageRequest.of(0, 20);
        when(courierRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(
                new Courier(1L, "Alice Johnson", CourierStatus.AVAILABLE, null, null),
                new Courier(2L, "Bob Martins", CourierStatus.BUSY, null, null))));

        Page<CourierResponse> result = courierAssignmentService.listCouriers(null, pageable);

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void getAvailableCouriers_delegatesToRepositoryWithAvailableStatus() {
        when(courierRepository.findByStatus(CourierStatus.AVAILABLE)).thenReturn(List.of());

        courierAssignmentService.getAvailableCouriers();

        verify(courierRepository).findByStatus(CourierStatus.AVAILABLE);
    }

    @Test
    void getMyCourier_whenLinked_returnsOwnCourierProfile() {
        asCourier(COURIER_USER_ID);
        Courier courier = new Courier(5L, "Alice Johnson", CourierStatus.AVAILABLE, null, COURIER_USER_ID);
        when(courierRepository.findByUserId(COURIER_USER_ID)).thenReturn(Optional.of(courier));

        CourierResponse response = courierAssignmentService.getMyCourier();

        assertThat(response.getId()).isEqualTo(5L);
    }

    @Test
    void getMyCourier_whenNotLinked_throwsEntityNotFoundException() {
        asCourier(OTHER_COURIER_USER_ID);
        when(courierRepository.findByUserId(OTHER_COURIER_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierAssignmentService.getMyCourier())
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAssignment_whenExists_returnsAssignmentWithCourierName() {
        CourierAssignment assignment = new CourierAssignment(1L, 10L, 5L, LocalDateTime.now());
        Courier courier = new Courier(5L, "Alice Johnson", CourierStatus.BUSY, null, COURIER_USER_ID);
        when(courierAssignmentRepository.findByOrderId(10L)).thenReturn(Optional.of(assignment));
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));

        AssignmentResponse response = courierAssignmentService.getAssignment(10L);

        assertThat(response.getOrderId()).isEqualTo(10L);
        assertThat(response.getCourierId()).isEqualTo(5L);
        assertThat(response.getCourierName()).isEqualTo("Alice Johnson");
    }

    @Test
    void getAssignment_whenNoneExists_throwsEntityNotFoundException() {
        when(courierAssignmentRepository.findByOrderId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierAssignmentService.getAssignment(10L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void createCourier_persistsWithAvailableStatusRegardlessOfRequest() {
        when(courierRepository.save(any(Courier.class))).thenAnswer(invocation -> {
            Courier courier = invocation.getArgument(0);
            courier.setId(42L);
            return courier;
        });

        CourierResponse response = courierAssignmentService.createCourier(new CreateCourierRequest("New Courier", null, null));

        ArgumentCaptor<Courier> captor = ArgumentCaptor.forClass(Courier.class);
        verify(courierRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("New Courier");
        assertThat(captor.getValue().getStatus()).isEqualTo(CourierStatus.AVAILABLE);

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getName()).isEqualTo("New Courier");
        assertThat(response.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
    }

    @Test
    void createCourier_withoutPhotoUrl_persistsSuccessfully() {
        when(courierRepository.save(any(Courier.class))).thenAnswer(invocation -> {
            Courier courier = invocation.getArgument(0);
            courier.setId(43L);
            return courier;
        });

        CourierResponse response = courierAssignmentService.createCourier(new CreateCourierRequest("No Photo Courier", null, null));

        assertThat(response.getPhotoUrl()).isNull();
    }

    @Test
    void createCourier_withPhotoUrl_persistsAndReturnsIt() {
        when(courierRepository.save(any(Courier.class))).thenAnswer(invocation -> {
            Courier courier = invocation.getArgument(0);
            courier.setId(44L);
            return courier;
        });

        CourierResponse response = courierAssignmentService.createCourier(
                new CreateCourierRequest("Photo Courier", "https://example.com/photo.jpg", null));

        ArgumentCaptor<Courier> captor = ArgumentCaptor.forClass(Courier.class);
        verify(courierRepository).save(captor.capture());
        assertThat(captor.getValue().getPhotoUrl()).isEqualTo("https://example.com/photo.jpg");
        assertThat(response.getPhotoUrl()).isEqualTo("https://example.com/photo.jpg");
    }

    @Test
    void createCourier_withUserId_persistsLinkToUserAccount() {
        when(courierRepository.save(any(Courier.class))).thenAnswer(invocation -> invocation.getArgument(0));

        courierAssignmentService.createCourier(new CreateCourierRequest("Linked Courier", null, COURIER_USER_ID));

        ArgumentCaptor<Courier> captor = ArgumentCaptor.forClass(Courier.class);
        verify(courierRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(COURIER_USER_ID);
    }

    @Test
    void changeStatus_asAdmin_fromAvailableToOffline_succeeds() {
        Courier courier = new Courier(1L, "Alice Johnson", CourierStatus.AVAILABLE, null, COURIER_USER_ID);
        when(courierRepository.findById(1L)).thenReturn(Optional.of(courier));

        CourierResponse response = courierAssignmentService.changeStatus(1L, CourierStatus.OFFLINE);

        assertThat(courier.getStatus()).isEqualTo(CourierStatus.OFFLINE);
        assertThat(response.getStatus()).isEqualTo(CourierStatus.OFFLINE);
        verify(courierRepository).save(courier);
    }

    @Test
    void changeStatus_asOwningCourier_succeeds() {
        asCourier(COURIER_USER_ID);
        Courier courier = new Courier(1L, "Alice Johnson", CourierStatus.AVAILABLE, null, COURIER_USER_ID);
        when(courierRepository.findById(1L)).thenReturn(Optional.of(courier));

        CourierResponse response = courierAssignmentService.changeStatus(1L, CourierStatus.OFFLINE);

        assertThat(response.getStatus()).isEqualTo(CourierStatus.OFFLINE);
    }

    @Test
    void changeStatus_asUnrelatedCourier_throwsAccessDenied() {
        asCourier(OTHER_COURIER_USER_ID);
        Courier courier = new Courier(1L, "Alice Johnson", CourierStatus.AVAILABLE, null, COURIER_USER_ID);
        when(courierRepository.findById(1L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> courierAssignmentService.changeStatus(1L, CourierStatus.OFFLINE))
                .isInstanceOf(AccessDeniedException.class);

        verify(courierRepository, never()).save(any());
    }

    @Test
    void changeStatus_fromOfflineToAvailable_succeeds() {
        Courier courier = new Courier(4L, "David Petrov", CourierStatus.OFFLINE, null, null);
        when(courierRepository.findById(4L)).thenReturn(Optional.of(courier));

        CourierResponse response = courierAssignmentService.changeStatus(4L, CourierStatus.AVAILABLE);

        assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(response.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
        verify(courierRepository).save(courier);
    }

    @Test
    void changeStatus_whenTargetIsBusy_throwsIllegalArgumentExceptionAndDoesNotPersist() {
        Courier courier = new Courier(1L, "Alice Johnson", CourierStatus.AVAILABLE, null, null);
        when(courierRepository.findById(1L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> courierAssignmentService.changeStatus(1L, CourierStatus.BUSY))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(courier.getStatus()).isEqualTo(CourierStatus.AVAILABLE);
        verify(courierRepository, never()).save(any());
    }

    @Test
    void changeStatus_whenCourierCurrentlyBusy_throwsCourierNotAvailableExceptionAndDoesNotPersist() {
        Courier courier = new Courier(5L, "Alice Johnson", CourierStatus.BUSY, null, null);
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> courierAssignmentService.changeStatus(5L, CourierStatus.AVAILABLE))
                .isInstanceOf(CourierNotAvailableException.class);

        assertThat(courier.getStatus()).isEqualTo(CourierStatus.BUSY);
        verify(courierRepository, never()).save(any());
    }

    @Test
    void changeStatus_whenCourierCurrentlyBusy_alsoRejectsOfflineTarget() {
        Courier courier = new Courier(5L, "Alice Johnson", CourierStatus.BUSY, null, null);
        when(courierRepository.findById(5L)).thenReturn(Optional.of(courier));

        assertThatThrownBy(() -> courierAssignmentService.changeStatus(5L, CourierStatus.OFFLINE))
                .isInstanceOf(CourierNotAvailableException.class);

        verify(courierRepository, never()).save(any());
    }

    @Test
    void changeStatus_whenCourierNotFound_throwsEntityNotFoundException() {
        when(courierRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierAssignmentService.changeStatus(99L, CourierStatus.OFFLINE))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listCouriers_includesAverageRatingAndCountFromRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Courier courier = new Courier(1L, "Alice Johnson", CourierStatus.AVAILABLE, "https://example.com/alice.jpg", null);
        when(courierRepository.findByStatus(CourierStatus.AVAILABLE, pageable))
                .thenReturn(new PageImpl<>(List.of(courier)));
        when(courierRatingRepository.findAverageRatingByCourierId(1L)).thenReturn(4.5);
        when(courierRatingRepository.countByCourierId(1L)).thenReturn(12L);

        Page<CourierResponse> result = courierAssignmentService.listCouriers(CourierStatus.AVAILABLE, pageable);

        assertThat(result.getContent().get(0).getAverageRating()).isEqualTo(4.5);
        assertThat(result.getContent().get(0).getRatingCount()).isEqualTo(12);
        assertThat(result.getContent().get(0).getPhotoUrl()).isEqualTo("https://example.com/alice.jpg");
    }

    @Test
    void registerCourierAccount_whenNotLinked_createsAvailableCourier() {
        when(courierRepository.findByUserId(COURIER_USER_ID)).thenReturn(Optional.empty());

        courierAssignmentService.registerCourierAccount(COURIER_USER_ID, "Jane Rider");

        ArgumentCaptor<Courier> captor = ArgumentCaptor.forClass(Courier.class);
        verify(courierRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Jane Rider");
        assertThat(captor.getValue().getStatus()).isEqualTo(CourierStatus.AVAILABLE);
        assertThat(captor.getValue().getUserId()).isEqualTo(COURIER_USER_ID);
    }

    @Test
    void registerCourierAccount_whenAlreadyLinked_doesNotCreateDuplicate() {
        Courier existing = new Courier(5L, "Jane Rider", CourierStatus.AVAILABLE, null, COURIER_USER_ID);
        when(courierRepository.findByUserId(COURIER_USER_ID)).thenReturn(Optional.of(existing));

        courierAssignmentService.registerCourierAccount(COURIER_USER_ID, "Jane Rider");

        verify(courierRepository, never()).save(any());
    }

    @Test
    void listCouriers_whenNoRatingsExist_returnsNullAverageAndZeroCount() {
        Pageable pageable = PageRequest.of(0, 20);
        Courier courier = new Courier(1L, "Alice Johnson", CourierStatus.AVAILABLE, null, null);
        when(courierRepository.findByStatus(CourierStatus.AVAILABLE, pageable))
                .thenReturn(new PageImpl<>(List.of(courier)));
        when(courierRatingRepository.findAverageRatingByCourierId(1L)).thenReturn(null);
        when(courierRatingRepository.countByCourierId(1L)).thenReturn(0L);

        Page<CourierResponse> result = courierAssignmentService.listCouriers(CourierStatus.AVAILABLE, pageable);

        assertThat(result.getContent().get(0).getAverageRating()).isNull();
        assertThat(result.getContent().get(0).getRatingCount()).isEqualTo(0);
    }
}