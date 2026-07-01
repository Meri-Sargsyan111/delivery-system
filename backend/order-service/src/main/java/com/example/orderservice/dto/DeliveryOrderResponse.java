package com.example.orderservice.dto;

import com.example.orderservice.order.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOrderResponse {

    private Long id;
    private String customerName;
    private String fromAddress;
    private String toAddress;
    private OrderStatus status;
}
