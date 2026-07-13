package com.example.orderservice.specification;

import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.order.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderSpecification {

    public static Specification<DeliveryOrder> withFilters(String customerName, OrderStatus status) {
        return withFilters(customerName, status, null, null);
    }

    /**
     * @param ownerCustomerUserId if non-null, restricts results to orders owned by this customer
     * @param ownerCourierUserId  if non-null, restricts results to orders assigned to this courier
     *                            Exactly one of these should be non-null for a scoped (non-admin) search;
     *                            both null means unrestricted (admin) search.
     */
    public static Specification<DeliveryOrder> withFilters(
            String customerName, OrderStatus status, UUID ownerCustomerUserId, UUID ownerCourierUserId) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (customerName != null) {
                predicates.add(cb.equal(root.get("customerName"), customerName));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (ownerCustomerUserId != null) {
                predicates.add(cb.equal(root.get("customerUserId"), ownerCustomerUserId));
            }

            if (ownerCourierUserId != null) {
                predicates.add(cb.equal(root.get("courierUserId"), ownerCourierUserId));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
