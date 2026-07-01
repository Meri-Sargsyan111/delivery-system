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
    DeliveryOrder toEntity(CreateOrderRequest request);

    DeliveryOrderResponse toResponse(DeliveryOrder order);
}
