package com.example.courierservice.courier;

/**
 * The vehicle a courier actually drives - distinct from order-service's
 * RecommendedVehicle (a pricing/capacity recommendation computed at order creation
 * time, not a property of any specific courier). Surfaced by tracking-service's live
 * tracking API.
 */
public enum VehicleType {
    CAR,
    MOTORCYCLE,
    BICYCLE,
    VAN
}
