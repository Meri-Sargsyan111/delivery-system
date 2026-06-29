package com.example.orderservice.specification;

import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.order.OrderStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class OrderSpecification {

    public static Specification<DeliveryOrder> withFilters(String customerName, OrderStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (customerName != null) {
                predicates.add(cb.equal(root.get("customerName"), customerName));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
