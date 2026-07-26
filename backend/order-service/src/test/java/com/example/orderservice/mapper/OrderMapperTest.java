package com.example.orderservice.mapper;

import com.example.orderservice.dto.DeliveryOrderResponse;
import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.order.OrderStatus;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

class OrderMapperTest {

    private final OrderMapper orderMapper = Mappers.getMapper(OrderMapper.class);

    @Test
    void toResponse_includesCustomerPhone() {
        DeliveryOrder order = new DeliveryOrder(
                1L, "John", "A", "B", OrderStatus.CREATED, null, "+37499123456", null, null,
                null, null, null, null);

        DeliveryOrderResponse response = orderMapper.toResponse(order);

        assertThat(response.getCustomerPhone()).isEqualTo("+37499123456");
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getCustomerName()).isEqualTo("John");
    }
}