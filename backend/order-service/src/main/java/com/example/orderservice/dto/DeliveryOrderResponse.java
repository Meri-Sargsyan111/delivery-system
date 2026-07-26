package com.example.orderservice.dto;

import com.example.orderservice.order.OrderStatus;
import com.example.orderservice.order.PaymentMethod;
import com.example.orderservice.order.RecommendedVehicle;
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
    private Long courierId;
    private String customerPhone;
    private String packageDescription;
    private Double weightKg;
    private PaymentMethod paymentMethod;
    private RecommendedVehicle recommendedVehicle;
}