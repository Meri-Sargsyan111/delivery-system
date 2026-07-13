package com.example.courierservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourierRegisteredEvent {

    private UUID userId;
    private String firstName;
    private String lastName;
}
