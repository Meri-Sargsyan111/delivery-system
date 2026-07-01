package com.example.orderservice.service;

import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.entity.DeliveryOrder;
import com.example.orderservice.event.OrderCreatedEvent;
import com.example.orderservice.exception.EntityNotFoundException;
import com.example.orderservice.mapper.OrderMapper;
import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.repository.DeliveryOrderRepository;
import com.example.orderservice.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private DeliveryOrderRepository orderRepository;
    @Mock private KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    @Mock private OrderMapper orderMapper;

    @InjectMocks private OrderServiceImpl orderService;
    @Test
    void createOrder_validRequest_persistsWithCreatedStatusAndPublishesToKafka() {
        CreateOrderRequest request = new CreateOrderRequest("John", "From St", "To St");
        DeliveryOrder entity = new DeliveryOrder(1L, "John", "From St", "To St", null);

        when(orderMapper.toEntity(request)).thenReturn(entity);
        when(orderRepository.save(entity)).thenReturn(entity);

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("Order created");
        assertThat(entity.getStatus()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository).save(entity);
        verify(kafkaTemplate).send(eq("new-orders"), any(OrderCreatedEvent.class));
    }

    @Test
    void createOrder_whenRepositoryThrows_rethrowsExceptionAndSkipsKafka() {
        CreateOrderRequest request = new CreateOrderRequest("John", "From St", "To St");
        DeliveryOrder entity = new DeliveryOrder(null, "John", "From St", "To St", null);

        when(orderMapper.toEntity(request)).thenReturn(entity);
        when(orderRepository.save(entity)).thenThrow(new RuntimeException("DB unavailable"));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB unavailable");

        verify(kafkaTemplate, never()).send(anyString(), any());
    }


    @Test
    void getOrders_returnsPageFromRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<DeliveryOrder> expected = new PageImpl<>(List.of());
        when(orderRepository.findAll(pageable)).thenReturn(expected);

        Page<DeliveryOrder> result = orderService.getOrders(pageable);

        assertThat(result).isEqualTo(expected);
        verify(orderRepository).findAll(pageable);
    }



    @Test
    void getOrderById_whenOrderExists_returnsOrder() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        DeliveryOrder result = orderService.getOrderById(1L);

        assertThat(result).isEqualTo(order);
    }

    @Test
    void getOrderById_whenOrderDoesNotExist_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }


    @Test
    void searchOrders_withFilters_delegatesSpecificationToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<DeliveryOrder> expected = new PageImpl<>(List.of());
        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<DeliveryOrder> result = orderService.searchOrders("John", OrderStatus.CREATED, pageable);

        assertThat(result).isEqualTo(expected);
        verify(orderRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void searchOrders_withNoFilters_stillDelegatesToRepository() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<DeliveryOrder> expected = new PageImpl<>(List.of());
        when(orderRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(expected);

        Page<DeliveryOrder> result = orderService.searchOrders(null, null, pageable);

        assertThat(result).isEqualTo(expected);
    }



    @Test
    void assignOrder_changesStatusToAssignedAndPersists() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.assignOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("Order assigned");
        verify(orderRepository).save(order);
    }

    @Test
    void assignOrder_whenOrderNotFound_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.assignOrder(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");

        verify(orderRepository, never()).save(any());
    }


    @Test
    void deliverOrder_changesStatusToDeliveredAndPersists() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.ASSIGNED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.deliverOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("Order delivered");
        verify(orderRepository).save(order);
    }

    @Test
    void deliverOrder_whenOrderNotFound_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.deliverOrder(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }


    @Test
    void cancelOrder_changesStatusToCancelledAndPersists() {
        DeliveryOrder order = new DeliveryOrder(1L, "John", "A", "B", OrderStatus.CREATED);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        OrderResponse response = orderService.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getMessage()).isEqualTo("Order cancelled");
        verify(orderRepository).save(order);
    }

    @Test
    void cancelOrder_whenOrderNotFound_throwsEntityNotFoundException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");
    }
}