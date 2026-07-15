package com.example.orderservice.mapper;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.DeliveryOrderResponse;
import com.example.orderservice.entity.DeliveryOrder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "customerPhone", expression = "java(trim(request.getCustomerPhone()))")
    DeliveryOrder toEntity(CreateOrderRequest request);

    DeliveryOrderResponse toResponse(DeliveryOrder order);

    /**
     * Trims only surrounding whitespace, preserving internal formatting
     * (e.g. "+1 202 555 0123" keeps its spaces) since this project has no
     * other phone-normalization convention to follow.
     */
    default String trim(String value) {
        return value == null ? null : value.trim();
    }
}
