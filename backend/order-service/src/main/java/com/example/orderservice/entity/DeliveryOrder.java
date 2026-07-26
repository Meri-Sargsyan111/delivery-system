package com.example.orderservice.entity;

import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.order.PaymentMethod;
import com.example.orderservice.order.RecommendedVehicle;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "delivery_orders", indexes = {
        @Index(name = "idx_delivery_orders_customer_user_id", columnList = "customerUserId"),
        @Index(name = "idx_delivery_orders_courier_user_id", columnList = "courierUserId")
})
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerName;

    private String fromAddress;

    private String toAddress;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    private Long courierId;

    private String customerPhone;

    /**
     * The auth-service user id (JWT "sub") of the customer who created this order.
     * Set once, from SecurityContext, at creation - never trusted from the client.
     * Null on orders that existed before ownership tracking was introduced; those
     * legacy rows are intentionally admin-only (see OrderServiceImpl) rather than
     * guessed-assigned to any user.
     */
    private UUID customerUserId;

    /**
     * The auth-service user id of the courier currently assigned to this order,
     * propagated from courier-service at assignment time (see CourierServiceClient).
     * Distinct from courierId, which is courier-service's own record id and carries
     * no provable link to the authenticated user operating that courier.
     */
    private UUID courierUserId;

    private String packageDescription;

    private Double weightKg;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    /**
     * Computed automatically at creation from weight/packageDescription (see
     * OrderServiceImpl.buildNewOrder and VehicleRecommender) - never client-supplied.
     */
    @Enumerated(EnumType.STRING)
    private RecommendedVehicle recommendedVehicle;

    /**
     * Set only for orders created via payment-service's internal endpoint (see
     * OrderServiceImpl.createOrderFromPayment) - null for orders created directly through
     * the original POST /orders path. Doubles as the idempotency key for that endpoint:
     * a retried call with the same paymentId finds the existing order instead of creating
     * a second one (see DeliveryOrderRepository.findBySourcePaymentId).
     */
    private UUID sourcePaymentId;

    /** Pre-sourcePaymentId constructor, kept so existing call sites/tests need no changes. */
    public DeliveryOrder(Long id, String customerName, String fromAddress, String toAddress,
                          OrderStatus status, Long courierId, String customerPhone, UUID customerUserId,
                          UUID courierUserId, String packageDescription, Double weightKg,
                          PaymentMethod paymentMethod, RecommendedVehicle recommendedVehicle) {
        this(id, customerName, fromAddress, toAddress, status, courierId, customerPhone, customerUserId,
                courierUserId, packageDescription, weightKg, paymentMethod, recommendedVehicle, null);
    }
}