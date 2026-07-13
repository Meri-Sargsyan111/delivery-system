package com.example.orderservice.service;

import com.example.orderservice.client.CourierReservationResult;
import com.example.orderservice.client.CourierServiceClient;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.DeliveryOrderResponse;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.OrderStatusView;
import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.event.DeliveryUpdateEvent;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.exception.CourierAssignmentException;
import com.example.orderservice.exception.EntityNotFoundException;
import com.example.orderservice.exception.InvalidOrderStateException;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.repository.DeliveryOrderRepository;
import com.example.orderservice.security.CurrentUser;
import com.example.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceImplTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();
    private static final UUID COURIER_USER_ID = UUID.randomUUID();
    private static final UUID OTHER_COURIER_USER_ID = UUID.randomUUID();

    @Mock private DeliveryOrderRepository orderRepository;
    @Mock private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    @Mock private KafkaTemplate<String, DeliveryUpdateEvent> deliveryUpdateKafkaTemplate;
    @Mock private OrderMapper orderMapper;
    @Mock private CourierServiceClient courierServiceClient;
    @Mock private CurrentUser currentUser;

    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(orderRepository, kafkaTemplate, deliveryUpdateKafkaTemplate,
                orderMapper, courierServiceClient, currentUser);
        asAdmin();
    }

    private void asAdmin() {
        lenient().when(currentUser.isAuthenticated()).thenReturn(true);
        lenient().when(currentUser.isAdmin()).thenReturn(true);
        lenient().when(currentUser.isCustomer()).thenReturn(false);
        lenient().when(currentUser.isCourier()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(ADMIN_ID);
    }

    private void asCustomer(UUID userId) {
        lenient().when(currentUser.isAuthenticated()).thenReturn(true);
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.isCustomer()).thenReturn(true);
        lenient().when(currentUser.isCourier()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(userId);
    }

    private void asCourier(UUID userId) {
        lenient().when(currentUser.isAuthenticated()).thenReturn(true);
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.isCustomer()).thenReturn(false);
        lenient().when(currentUser.isCourier()).thenReturn(true);
        lenient().when(currentUser.getUserId()).thenReturn(userId);
    }

    @Test
    void createOrder_validRequest_persistsWithCreatedStatusAndOwnerFromSecurityContextAndPublishesToKafka() {
        asCustomer(CUSTOMER_ID);
        CreateOrderRequest request = new CreateOrderRequest("John", "From St", "To St", "+37499123456");
        DeliveryOrder entity = new DeliveryOrder(1L, "John", "From St", "To St", null, null, "+37499123456", null, null);

        when(orderMapper.toEntity(request)).thenReturn(entity);
        when(orderRepository.save(entity)).thenReturn(entity);

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("Order created");
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CREATED);

        assertThat(entity.getCustomerUserId()).isEqualTo(CUSTOMER_ID);
        verify(orderRepository).save(entity);
        verify(kafkaTemplate).send(eq("new-orders"), any(OrderCreatedEvent.class));
    }

    @Test
    void createOrder_whenRepositoryThrows_rethrowsExceptionAndSkipsKafka() {
        CreateOrderRequest request = new CreateOrderRequest("John", "From St", "To St", "+37499123456");
        DeliveryOrder entity = new DeliveryOrder(null, "John", "From St", "To St", null, null, "+37499123456", null, null);

        when(orderMapper.toEntity(request)).thenReturn(entity);
        when(orderRepository.save(entity)).thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");

        verify(kafkaTemplate, never()).send(anyString(), any());
    }

    @Test
    void getOrders_asAdmin_returnsAllOrders() {
        Pageable pageable = PageRequest.of(0, 20);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", null, null);
        DeliveryOrderResponse response = new DeliveryOrderResponse(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456");
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order)));
        when(orderMapper.toResponse(order)).thenReturn(response);

        Page<DeliveryOrderResponse> result = orderService.getOrders(pageable);

        assertThat(result.getContent()).containsExactly(response);
        verify(orderRepository).findAll(pageable);
    }

    @Test
    void getOrders_asCustomer_scopesQueryToOwnCustomerUserId() {
        asCustomer(CUSTOMER_ID);
        Pageable pageable = PageRequest.of(0, 20);
        Page<DeliveryOrder> expected = new PageImpl<>(List.of());
        when(orderRepository.findByCustomerUserId(CUSTOMER_ID, pageable)).thenReturn(expected);

        Page<DeliveryOrderResponse> result = orderService.getOrders(pageable);

        assertThat(result.getContent()).isEmpty();
        verify(orderRepository).findByCustomerUserId(CUSTOMER_ID, pageable);
        verify(orderRepository, never()).findAll(pageable);
    }

    @Test
    void getOrders_asCourier_scopesQueryToOwnCourierUserId() {
        asCourier(COURIER_USER_ID);
        Pageable pageable = PageRequest.of(0, 20);
        Page<DeliveryOrder> expected = new PageImpl<>(List.of());
        when(orderRepository.findByCourierUserId(COURIER_USER_ID, pageable)).thenReturn(expected);

        Page<DeliveryOrderResponse> result = orderService.getOrders(pageable);

        assertThat(result.getContent()).isEmpty();
        verify(orderRepository).findByCourierUserId(COURIER_USER_ID, pageable);
        verify(orderRepository, never()).findAll(pageable);
    }

    @Test
    void getOrderById_whenOrderExists_returnsOrder() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", null, null);
        DeliveryOrderResponse response = new DeliveryOrderResponse(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        DeliveryOrderResponse result = orderService.getOrderById(1L);

        assertThat(result).isEqualTo(response);
    }

    @Test
    void getOrderById_whenOrderDoesNotExist_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getOrderById_asOwningCustomer_returnsOrder() {
        asCustomer(CUSTOMER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, null);
        DeliveryOrderResponse response = new DeliveryOrderResponse(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        assertThat(orderService.getOrderById(1L)).isEqualTo(response);
    }

    @Test
    void getOrderById_asUnrelatedCustomer_throwsAccessDenied() {
        asCustomer(OTHER_CUSTOMER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getOrderById_asAssignedCourier_returnsOrder() {
        asCourier(COURIER_USER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        DeliveryOrderResponse response = new DeliveryOrderResponse(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456");
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderMapper.toResponse(order)).thenReturn(response);

        assertThat(orderService.getOrderById(1L)).isEqualTo(response);
    }

    @Test
    void getOrderById_asUnrelatedCourier_throwsAccessDenied() {
        asCourier(OTHER_COURIER_USER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderById(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getOrderById_legacyOrderWithNoOwner_isNotAccessibleByCustomerOrCourier() {
        DeliveryOrder legacyOrder = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, null, null, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(legacyOrder));

        asCustomer(CUSTOMER_ID);
        assertThatThrownBy(() -> orderService.getOrderById(1L)).isInstanceOf(AccessDeniedException.class);

        asCourier(COURIER_USER_ID);
        assertThatThrownBy(() -> orderService.getOrderById(1L)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getOrderByIdInternal_returnsStatusProjectionWithNoOwnershipCheck() {
        asCustomer(OTHER_CUSTOMER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        OrderStatusView result = orderService.getOrderByIdInternal(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(result.getCustomerUserId()).isEqualTo(CUSTOMER_ID);
        assertThat(result.getCourierUserId()).isEqualTo(COURIER_USER_ID);
    }

    @Test
    void searchOrders_asAdmin_appliesNoOwnershipRestriction() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<DeliveryOrder> expected = new PageImpl<>(List.of());
        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<DeliveryOrderResponse> result = orderService.searchOrders("John", OrderStatus.CREATED, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(orderRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchOrders_withNoFilters_stillDelegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<DeliveryOrder> expected = new PageImpl<>(List.of());
        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<DeliveryOrderResponse> result = orderService.searchOrders(null, null, pageable);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void searchOrders_asCustomer_stillScopedByRepositoryQuery() {
        asCustomer(CUSTOMER_ID);
        Pageable pageable = PageRequest.of(0, 20);
        Page<DeliveryOrder> expected = new PageImpl<>(List.of());
        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<DeliveryOrderResponse> result = orderService.searchOrders(null, null, pageable);

        assertThat(result.getContent()).isEmpty();
        verify(orderRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void assignOrder_whenCreatedAndCourierReserved_changesStatusToAssignedAndPersistsCourierIdentifiers() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);
        when(courierServiceClient.reserveCourier(5L, 1L)).thenReturn(new CourierReservationResult(5L, COURIER_USER_ID));

        OrderResponse response = orderService.assignOrder(1L, 5L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(order.getCourierId()).isEqualTo(5L);
        assertThat(order.getCourierUserId()).isEqualTo(COURIER_USER_ID);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("Order assigned");
        verify(courierServiceClient).reserveCourier(5L, 1L);
        verify(orderRepository).save(order);
    }

    @Test
    void assignOrder_whenOrderNotFound_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.assignOrder(99L, 5L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(courierServiceClient);
    }

    @Test
    void assignOrder_whenOrderAlreadyAssigned_throwsInvalidOrderStateAndSkipsCourierReservation() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 3L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.assignOrder(1L, 5L))
                .isInstanceOf(InvalidOrderStateException.class);

        verifyNoInteractions(courierServiceClient);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void assignOrder_whenOrderDelivered_throwsInvalidOrderState() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.DELIVERED, 3L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.assignOrder(1L, 5L))
                .isInstanceOf(InvalidOrderStateException.class);

        verifyNoInteractions(courierServiceClient);
    }

    @Test
    void assignOrder_whenOrderCancelled_throwsInvalidOrderState() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CANCELLED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.assignOrder(1L, 5L))
                .isInstanceOf(InvalidOrderStateException.class);

        verifyNoInteractions(courierServiceClient);
    }

    @Test
    void assignOrder_whenCourierNotAvailable_propagatesExceptionAndDoesNotChangeOrder() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        doThrow(new CourierAssignmentException(HttpStatus.CONFLICT, "Courier 5 is not available"))
                .when(courierServiceClient).reserveCourier(5L, 1L);

        assertThatThrownBy(() -> orderService.assignOrder(1L, 5L))
                .isInstanceOf(CourierAssignmentException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void startProgress_whenAssigned_changesStatusToInProgress() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.startProgress(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
        verify(orderRepository).save(order);
    }

    @Test
    void startProgress_calledFromUnauthenticatedKafkaConsumerContext_stillWorks() {

        lenient().when(currentUser.isAuthenticated()).thenReturn(false);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.startProgress(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
    }

    @Test
    void startProgress_whenNotAssigned_throwsInvalidOrderState() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.startProgress(1L))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void deliverOrder_whenAssigned_changesStatusToDeliveredAndPersists() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.deliverOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("Order delivered");
        verify(orderRepository).save(order);
    }

    @Test
    void deliverOrder_asAssignedCourier_succeeds() {
        asCourier(COURIER_USER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.deliverOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void deliverOrder_asUnrelatedCourier_throwsAccessDenied() {
        asCourier(OTHER_COURIER_USER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deliverOrder(1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void deliverOrder_asUnrelatedCustomer_throwsAccessDenied() {
        asCustomer(OTHER_CUSTOMER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deliverOrder(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void deliverOrder_calledFromUnauthenticatedKafkaConsumerContext_stillWorks() {
        lenient().when(currentUser.isAuthenticated()).thenReturn(false);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.deliverOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void deliverOrder_whenInProgress_changesStatusToDelivered() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.IN_PROGRESS, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.deliverOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void deliverOrder_whenOrderNotFound_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deliverOrder(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void deliverOrder_whenStillCreated_throwsInvalidOrderState() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deliverOrder(1L))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void deliverOrder_whenAlreadyCancelled_throwsInvalidOrderState() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CANCELLED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.deliverOrder(1L))
                .isInstanceOf(InvalidOrderStateException.class);
    }

    @Test
    void cancelOrder_whenCreated_changesStatusToCancelledAndSkipsKafka() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("Order cancelled");
        verify(orderRepository).save(order);
        verifyNoInteractions(deliveryUpdateKafkaTemplate);
    }

    @Test
    void cancelOrder_asOwningCustomer_succeeds() {
        asCustomer(CUSTOMER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancelOrder_asUnrelatedCustomer_throwsAccessDenied() {
        asCustomer(OTHER_CUSTOMER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", CUSTOMER_ID, null);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(AccessDeniedException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelOrder_asCourier_throwsAccessDeniedEvenIfAssigned() {
        asCourier(COURIER_USER_ID);
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelOrder_whenAssigned_publishesCancelledEventToFreeCourier() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        ArgumentCaptor<DeliveryUpdateEvent> captor = ArgumentCaptor.forClass(DeliveryUpdateEvent.class);
        verify(deliveryUpdateKafkaTemplate).send(eq("delivery-updates"), captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo("CANCELLED");
    }

    @Test
    void cancelOrder_whenOrderNotFound_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void cancelOrder_whenAlreadyDelivered_throwsInvalidOrderState() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.DELIVERED, 5L, "+37499123456", CUSTOMER_ID, COURIER_USER_ID);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(orderRepository, never()).save(any());
    }
}