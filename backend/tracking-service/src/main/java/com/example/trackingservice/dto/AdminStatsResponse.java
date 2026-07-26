package com.example.trackingservice.dto;

public record AdminStatsResponse(
        long activeDeliveries,
        long activeCouriers,
        long completedToday
) {}
