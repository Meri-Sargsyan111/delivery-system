package com.example.courierservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "courier_updates")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long orderId;
    private String courierName;
    private String status;
}