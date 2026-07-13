package com.example.courierservice.service;

import com.example.courierservice.client.OrderServiceClient;
import com.example.courierservice.dto.AssignmentResponse;
import com.example.courierservice.dto.RateOrderRequest;
import com.example.courierservice.dto.RatingResponse;
import com.example.courierservice.dto.RemoteOrderView;
import com.example.courierservice.entity.CourierRating;
import com.example.courierservice.exception.EntityNotFoundException;
import com.example.courierservice.exception.InvalidOrderStateException;
import com.example.courierservice.exception.OrderAlreadyRatedException;
import com.example.courierservice.repository.CourierRatingRepository;
import com.example.courierservice.security.CurrentUser;
import com.example.courierservice.service.impl.CourierRatingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CourierRatingServiceImplTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();

    @Mock private CourierRatingRepository courierRatingRepository;
    @Mock private CourierAssignmentService courierAssignmentService;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private CurrentUser currentUser;

    @InjectMocks private CourierRatingServiceImpl courierRatingService;

    @BeforeEach
    void setUp() {
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(CUSTOMER_ID);
    }

    @Test
    void rateOrder_whenDeliveredAndNotYetRated_persistsRatingForAssignedCourier() {
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "DELIVERED", CUSTOMER_ID, null));
        when(courierRatingRepository.existsByOrderId(10L)).thenReturn(false);

        RatingResponse response = courierRatingService.rateOrder(10L, new RateOrderRequest(5));

        ArgumentCaptor<CourierRating> captor = ArgumentCaptor.forClass(CourierRating.class);
        verify(courierRatingRepository).save(captor.capture());

        CourierRating saved = captor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(10L);
        assertThat(saved.getCourierId()).isEqualTo(5L);
        assertThat(saved.getValue()).isEqualTo(5);
        assertThat(saved.getRatedAt()).isNotNull();

        assertThat(response.getOrderId()).isEqualTo(10L);
        assertThat(response.getCourierId()).isEqualTo(5L);
        assertThat(response.getValue()).isEqualTo(5);
    }

    @Test
    void rateOrder_asUnrelatedCustomer_throwsAccessDeniedAndDoesNotPersist() {
        lenient().when(currentUser.getUserId()).thenReturn(OTHER_CUSTOMER_ID);
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "DELIVERED", CUSTOMER_ID, null));

        assertThatThrownBy(() -> courierRatingService.rateOrder(10L, new RateOrderRequest(5)))
                .isInstanceOf(AccessDeniedException.class);

        verify(courierRatingRepository, never()).save(any());
    }

    @Test
    void rateOrder_whenOrderHasNoRecordedOwner_throwsAccessDeniedForNonAdmin() {

        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "DELIVERED", null, null));

        assertThatThrownBy(() -> courierRatingService.rateOrder(10L, new RateOrderRequest(5)))
                .isInstanceOf(AccessDeniedException.class);

        verify(courierRatingRepository, never()).save(any());
    }

    @Test
    void rateOrder_asAdmin_bypassesOwnershipCheck() {
        lenient().when(currentUser.isAdmin()).thenReturn(true);
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "DELIVERED", OTHER_CUSTOMER_ID, null));
        when(courierRatingRepository.existsByOrderId(10L)).thenReturn(false);

        courierRatingService.rateOrder(10L, new RateOrderRequest(5));

        verify(courierRatingRepository).save(any(CourierRating.class));
    }

    @Test
    void rateOrder_whenOrderNotDelivered_throwsInvalidOrderStateAndDoesNotPersist() {
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "IN_PROGRESS", CUSTOMER_ID, null));

        assertThatThrownBy(() -> courierRatingService.rateOrder(10L, new RateOrderRequest(5)))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(courierRatingRepository, never()).save(any());
    }

    @Test
    void rateOrder_whenAlreadyRated_throwsOrderAlreadyRatedExceptionAndDoesNotPersist() {
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "DELIVERED", CUSTOMER_ID, null));
        when(courierRatingRepository.existsByOrderId(10L)).thenReturn(true);

        assertThatThrownBy(() -> courierRatingService.rateOrder(10L, new RateOrderRequest(5)))
                .isInstanceOf(OrderAlreadyRatedException.class);

        verify(courierRatingRepository, never()).save(any());
    }

    @Test
    void rateOrder_whenNoAssignmentExists_throwsEntityNotFoundExceptionAndDoesNotCallOrderService() {
        when(courierAssignmentService.getAssignment(10L))
                .thenThrow(new EntityNotFoundException("No assignment found for order: 10"));

        assertThatThrownBy(() -> courierRatingService.rateOrder(10L, new RateOrderRequest(5)))
                .isInstanceOf(EntityNotFoundException.class);

        verify(orderServiceClient, never()).getOrder(any());
        verify(courierRatingRepository, never()).save(any());
    }

    @Test
    void rateOrder_whenConcurrentDuplicateSlipsPastPreCheck_translatesDbConstraintViolationToOrderAlreadyRated() {
        when(courierAssignmentService.getAssignment(10L))
                .thenReturn(new AssignmentResponse(10L, 5L, "Alice Johnson", null));
        when(orderServiceClient.getOrder(10L)).thenReturn(new RemoteOrderView(10L, "DELIVERED", CUSTOMER_ID, null));
        when(courierRatingRepository.existsByOrderId(10L)).thenReturn(false);
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(courierRatingRepository).save(any(CourierRating.class));

        assertThatThrownBy(() -> courierRatingService.rateOrder(10L, new RateOrderRequest(5)))
                .isInstanceOf(OrderAlreadyRatedException.class);
    }
}