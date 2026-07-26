package com.example.aiservice.extraction;

/**
 * Delivery details pulled out of a customer's free-text message before it's sent to
 * Ollama. Any field the extractor couldn't confidently find is {@code null} - callers
 * building the "Known information" block render that as "Unknown" rather than guessing.
 */
public record ExtractedDeliveryDetails(
        String pickupCity,
        String destinationCity,
        String weight,
        String packageType
) {
}
