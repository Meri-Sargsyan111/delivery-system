package com.example.orderservice.dto;

import lombok.Data;

@Data
public class CreateOrderRequest {

    private String customerName;
    private String fromAddress;
    private String toAddress;
}