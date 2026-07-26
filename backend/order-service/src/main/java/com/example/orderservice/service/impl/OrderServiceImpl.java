package com.example.orderservice.service.impl;

import com.example.orderservice.client.AuthServiceClient;
import com.example.orderservice.client.CourierReservationResult;
import com.example.orderservice.client.CourierServiceClient;
import com.example.orderservice.client.CustomerLookupResult;
import com.example.orderservice.dto.CreateOrderFromPaymentRequest;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.DeliveryOrderResponse;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.dto.OrderStatusView;
import com.example.orderservice.dto.UnassignOrderRequest;
import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.event.DeliveryUpdateEvent;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.exception.EntityNotFoundException;
import com.example.orderservice.exception.InvalidOrderStateException;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.order.PaymentMethod;
import com.example.orderservice.repository.DeliveryOrderRepository;
import com.example.orderservice.security.CurrentUser;
import com.example.orderservice.service.OrderService;
import com.example.orderservice.specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Set<OrderStatus> DELIVERABLE_FROM = EnumSet.of(OrderStatus.ASSIGNED, OrderStatus.IN_PROGRESS);
    private static final Set<OrderStatus> CANCELLABLE_FROM =
            EnumSet.of(OrderStatus.CREATED, OrderStatus.ASSIGNED, OrderStatus.IN_PROGRESS);

    private final DeliveryOrderRepository orderRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final KafkaTemplate<String, DeliveryUpdateEvent> deliveryUpdateKafkaTemplate;
    private final OrderMapper orderMapper;
    private final CourierServiceClient courierServiceClient;
    private final OrderCustomerResolver orderCustomerResolver;
    private final CurrentUser currentUser;
    private final VehicleRecommender vehicleRecommender;
    private final AuthServiceClient authServiceClient;

    @Override
    public OrderResponse createOrder(CreateOrderRequest request) {
        DeliveryOrder order = buildNewOrder(request);
        orderRepository.save(order);
        publishOrderCreatedEvent(order);

        log.info("Order {} created", order.getId());
        return new OrderResponse(order.getId(), "Order created");
    }

    private DeliveryOrder buildNewOrder(CreateOrderRequest request) {
        CustomerLookupResult customer = orderCustomerResolver.resolve(request);

        DeliveryOrder order = orderMapper.toEntity(request);
        order.setStatus(OrderStatus.CREATED);
        order.setCustomerUserId(customer.id());
        order.setCustomerName((customer.firstName() + " " + customer.lastName()).trim());
        order.setCustomerPhone(customer.phoneNumber());
        order.setRecommendedVehicle(
                vehicleRecommender.recommend(order.getWeightKg(), order.getPackageDescription()));
        return order;
    }

    /**
     * Called only via POST /orders/internal/from-payment (see InternalServiceTokenFilter)
     * after payment-service has verified a payment as SUCCEEDED. Idempotent on paymentId -
     * a retried call (payment-service's own inline retry, or its reconciliation sweep
     * racing a not-yet-committed prior attempt) finds the existing order and returns it
     * rather than creating a duplicate, independent of payment-service's own claim logic.
     */
    @Override
    public OrderResponse createOrderFromPayment(CreateOrderFromPaymentRequest request) {
        DeliveryOrder existing = orderRepository.findBySourcePaymentId(request.getPaymentId()).orElse(null);
        if (existing != null) {
            log.info("Order {} already exists for paymentId={}, returning existing order", existing.getId(), request.getPaymentId());
            return new OrderResponse(existing.getId(), "Order already exists for this payment");
        }

        CustomerLookupResult customer = authServiceClient.getCustomerById(request.getCustomerId());

        DeliveryOrder order = new DeliveryOrder();
        order.setFromAddress(request.getFromAddress());
        order.setToAddress(request.getToAddress());
        order.setPackageDescription(request.getPackageDescription());
        order.setWeightKg(request.getWeightKg());
        order.setStatus(OrderStatus.CREATED);
        order.setCustomerUserId(customer.id());
        order.setCustomerName((customer.firstName() + " " + customer.lastName()).trim());
        order.setCustomerPhone(customer.phoneNumber());
        order.setPaymentMethod(PaymentMethod.CARD);
        order.setRecommendedVehicle(vehicleRecommender.recommend(order.getWeightKg(), order.getPackageDescription()));
        order.setSourcePaymentId(request.getPaymentId());

        orderRepository.save(order);
        publishOrderCreatedEvent(order);

        log.info("Order {} created from paymentId={}", order.getId(), request.getPaymentId());
        return new OrderResponse(order.getId(), "Order created");
    }

    /**
     * Best-effort: the order row is already committed by the time this runs, so a
     * Kafka outage must not fail the request - doing so would report "order failed"
     * for an order that actually exists, inviting the customer to retry and create a
     * duplicate. tracking/notification/chat simply won't learn about this order until
     * it's manually reconciled; that's an existing-data gap either way this failure is
     * handled, not something recoverable from here.
     */
    private void publishOrderCreatedEvent(DeliveryOrder order) {
        try {
            kafkaTemplate.send("new-orders",
                    new OrderCreatedEvent(
                            order.getId(),
                            order.getCustomerName(),
                            order.getFromAddress(),
                            order.getToAddress(),
                            order.getCustomerUserId()));
        } catch (Exception e) {
            log.error("Order {} was created but publishing to Kafka failed", order.getId(), e);
        }
    }

    @Override
    public Page<DeliveryOrderResponse> getOrders(Pageable pageable) {
        Page<DeliveryOrder> orders;
        if (currentUser.isAdmin()) {
            orders = orderRepository.findAll(pageable);
        } else if (currentUser.isCourier()) {
            orders = orderRepository.findByCourierUserId(currentUser.getUserId(), pageable);
        } else {
            orders = orderRepository.findByCustomerUserId(currentUser.getUserId(), pageable);
        }
        return orders.map(orderMapper::toResponse);
    }

    @Override
    public DeliveryOrderResponse getOrderById(Long id) {
        DeliveryOrder order = findOrder(id);
        requireAccess(order);
        return orderMapper.toResponse(order);
    }

    @Override
    public OrderStatusView getOrderByIdInternal(Long id) {
        DeliveryOrder order = findOrder(id);
        return new OrderStatusView(order.getId(), order.getStatus(), order.getCustomerUserId(), order.getCourierUserId());
    }

    @Override
    public Page<DeliveryOrderResponse> searchOrders(String customerName, OrderStatus status, Pageable pageable) {
        Page<DeliveryOrder> orders;
        if (currentUser.isAdmin()) {
            orders = orderRepository.findAll(OrderSpecification.withFilters(customerName, status), pageable);
        } else if (currentUser.isCourier()) {
            orders = orderRepository.findAll(
                    OrderSpecification.withFilters(customerName, status, null, currentUser.getUserId()), pageable);
        } else {
            orders = orderRepository.findAll(
                    OrderSpecification.withFilters(customerName, status, currentUser.getUserId(), null), pageable);
        }
        return orders.map(orderMapper::toResponse);
    }

    @Override
    public OrderResponse assignOrder(Long id, Long courierId) {
        DeliveryOrder order = findOrder(id);

        if (order.getStatus() != OrderStatus.CREATED) {
            throw new InvalidOrderStateException(
                    "Order " + id + " cannot be assigned: current status is " + order.getStatus());
        }

        CourierReservationResult reservation = courierServiceClient.reserveCourier(courierId, id);

        order.setStatus(OrderStatus.ASSIGNED);
        order.setCourierId(courierId);
        order.setCourierUserId(reservation.courierUserId());
        orderRepository.save(order);
        log.info("Order {} assigned to courier {} and status changed to ASSIGNED", id, courierId);
        return new OrderResponse(order.getId(), "Order assigned");
    }

    @Override
    public OrderResponse unassignOrder(Long id, UnassignOrderRequest request) {
        DeliveryOrder order = findOrder(id);

        if (order.getStatus() != OrderStatus.ASSIGNED || !request.getCourierId().equals(order.getCourierId())) {
            log.info("Order {} unassign ignored - not currently ASSIGNED to courier {} (status={}, courierId={})",
                    id, request.getCourierId(), order.getStatus(), order.getCourierId());
            return new OrderResponse(order.getId(), "Order unassign ignored - no matching outstanding assignment");
        }

        order.setStatus(OrderStatus.CREATED);
        order.setCourierId(null);
        order.setCourierUserId(null);
        orderRepository.save(order);
        log.info("Order {} unassigned from courier {} - status reverted to CREATED", id, request.getCourierId());
        return new OrderResponse(order.getId(), "Order unassigned");
    }

    @Override
    public void startProgress(Long id) {
        DeliveryOrder order = findOrder(id);

        if (order.getStatus() != OrderStatus.ASSIGNED) {
            throw new InvalidOrderStateException(
                    "Order " + id + " cannot start progress: current status is " + order.getStatus());
        }

        order.setStatus(OrderStatus.IN_PROGRESS);
        orderRepository.save(order);
        log.info("Order {} status changed to IN_PROGRESS", id);
    }

    @Override
    public OrderResponse deliverOrder(Long id) {
        DeliveryOrder order = findOrder(id);
        requireAdminOrAssignedCourier(order);

        if (!DELIVERABLE_FROM.contains(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Order " + id + " cannot be delivered: current status is " + order.getStatus());
        }

        order.setStatus(OrderStatus.DELIVERED);
        orderRepository.save(order);
        log.info("Order {} status changed to DELIVERED", id);
        return new OrderResponse(order.getId(), "Order delivered");
    }

    @Override
    public OrderResponse cancelOrder(Long id) {
        DeliveryOrder order = findOrder(id);
        requireAdminOrOwningCustomer(order);

        if (!CANCELLABLE_FROM.contains(order.getStatus())) {
            throw new InvalidOrderStateException(
                    "Order " + id + " cannot be cancelled: current status is " + order.getStatus());
        }

        boolean hadAssignedCourier = order.getStatus() != OrderStatus.CREATED && order.getCourierId() != null;

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Order {} status changed to CANCELLED", id);

        if (hadAssignedCourier) {
            publishCancelledDeliveryUpdate(order);
        }

        return new OrderResponse(order.getId(), "Order cancelled");
    }

    private void publishCancelledDeliveryUpdate(DeliveryOrder order) {
        try {
            deliveryUpdateKafkaTemplate.send("delivery-updates",
                    new DeliveryUpdateEvent(order.getId(), null, "CANCELLED", null));
            log.info("Published CANCELLED delivery update for order {} to free courier {}",
                    order.getId(), order.getCourierId());
        } catch (Exception e) {
            log.error("Order {} was cancelled but publishing the CANCELLED delivery update failed - " +
                    "the assigned courier's status was not freed by this event", order.getId(), e);
        }
    }

    private DeliveryOrder findOrder(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Order not found with id: {}", id);
                    return new EntityNotFoundException("Order not found with id: " + id);
                });
    }

    /**
     * ADMIN sees everything; CUSTOMER only their own order; COURIER only an order
     * assigned to them. Orders created before ownership tracking existed have a
     * null customerUserId/courierUserId and so never match a non-admin caller -
     * they remain safely admin-only rather than being guessed-assigned to anyone.
     */
    private void requireAccess(DeliveryOrder order) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (currentUser.isCustomer() && currentUser.getUserId().equals(order.getCustomerUserId())) {
            return;
        }
        if (currentUser.isCourier() && currentUser.getUserId().equals(order.getCourierUserId())) {
            return;
        }
        throw new AccessDeniedException("Not authorized to access order " + order.getId());
    }

    private void requireAdminOrOwningCustomer(DeliveryOrder order) {
        if (currentUser.isAdmin()) {
            return;
        }
        if (currentUser.isCustomer() && currentUser.getUserId().equals(order.getCustomerUserId())) {
            return;
        }
        throw new AccessDeniedException("Not authorized to modify order " + order.getId());
    }

    private void requireAdminOrAssignedCourier(DeliveryOrder order) {
        if (!currentUser.isAuthenticated() || currentUser.isAdmin()) {
            return;
        }
        if (currentUser.isCourier() && currentUser.getUserId().equals(order.getCourierUserId())) {
            return;
        }
        throw new AccessDeniedException("Not authorized to modify order " + order.getId());
    }
}