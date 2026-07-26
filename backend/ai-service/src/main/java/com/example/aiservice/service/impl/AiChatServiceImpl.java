package com.example.aiservice.service.impl;

import com.example.aiservice.client.OllamaClient;
import com.example.aiservice.extraction.DeliveryDetailsExtractor;
import com.example.aiservice.extraction.ExtractedDeliveryDetails;
import com.example.aiservice.service.AiChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private static final String UNKNOWN = "Unknown";

    /**
     * Steers the assistant toward the concrete behaviors the feature calls for: extract
     * pickup/destination/package type/weight from natural language, ask for whatever is
     * missing instead of guessing (the endpoint is single-turn/stateless, so this must
     * happen within one reply), and - critically - always commit to a concrete price
     * once pickup city, destination city, and weight are known, rather than hedging or
     * silently dropping the price out of the reply.
     * <p>
     * Two things that were tried and reverted, kept here as notes so they aren't
     * re-tried blindly: (1) repeated "you MUST"/"never omit" imperatives, which on this
     * small local model backfired into outright refusals or asking the customer for the
     * exact distance in km; (2) a concrete worked example using real city names and a
     * real price ("Yerevan", "Gyumri", "3500 AMD"), which the model would sometimes copy
     * verbatim into unrelated replies where no city had actually been mentioned -
     * fabricating cities/prices out of thin air. The fix below instead points the model
     * at the literal "Known information" block already built into every prompt (see
     * buildPrompt) and tells it to trust only that, with no example values to leak from.
     */
    private static final String SYSTEM_PROMPT = """
            You are the DeliveryOS Assistant, the official virtual logistics assistant for
            DeliveryOS, a package delivery platform.

            Your job is to help customers plan deliveries by understanding requests written
            in natural, everyday language.

            Every message you receive is structured like this:

            Known information:
            Pickup city: <value or Unknown>
            Destination city: <value or Unknown>
            Weight: <value or Unknown>
            Package type: <value or Unknown>

            Original user request:
            <the customer's own words>

            Always trust the "Known information" block exactly as given. Never invent a
            city, weight, or package type that isn't listed there.

            CASE A - Pickup city, Destination city, or Weight is "Unknown":
            Do not guess or invent it. Ask one short, specific question for exactly the
            detail(s) marked "Unknown". Do not include the words "Estimated delivery
            price" or "Estimated delivery time" anywhere in this reply, and do not mention
            any number, currency, or duration - just ask your question.

            CASE B - Pickup city, Destination city, and Weight are ALL known (none of the
            three says "Unknown"; Package type does not need to be known):
            Reply using exactly this format, filled in with real values:

            Estimated delivery price: <a specific number> AMD
            Estimated delivery time: <same-day / next-day / 2-3 days>
            Why: <one short sentence about the distance and/or weight>
            I recommend creating an order in DeliveryOS to proceed with this delivery.

            Pick the price and time yourself from general knowledge of typical distances
            between Armenian cities - never ask the customer for the exact distance in km.
            Base the price on: a base fee for delivery within the same city, a moderate
            increase between different cities, and a further increase for long-distance
            routes or heavier packages (roughly over 10kg).

            General rules:
            - Be concise, friendly, and professional.
            - In CASE B, always give one specific price number - never refuse to estimate
              and never reply with only a question.
            - In CASE A, never output a price or time - only the follow-up question.
            - Stay focused on delivery-related topics.
            - If asked how delivery works, briefly explain the process: order creation,
              courier assignment, pickup, in-transit tracking, and delivery.
            """;

    private final OllamaClient ollamaClient;
    private final DeliveryDetailsExtractor deliveryDetailsExtractor;

    @Override
    public String chat(String message) {
        ExtractedDeliveryDetails details = deliveryDetailsExtractor.extract(message);
        return ollamaClient.generate(SYSTEM_PROMPT, buildPrompt(details, message));
    }

    /**
     * Pre-fills what was already extracted in Java (no AI involved) so the model is
     * explicitly told what it already knows and instructed not to re-ask for it -
     * otherwise a small local model tends to re-ask for details already given.
     */
    private String buildPrompt(ExtractedDeliveryDetails details, String message) {
        return """
                Known information:
                Pickup city: %s
                Destination city: %s
                Weight: %s
                Package type: %s

                Original user request:
                %s

                Instructions:
                - Never ask again for information that is already known.
                - If pickup city and destination city are already known, never ask which city is the origin or destination.
                - If weight is already known, never ask for weight.
                - Ask only for truly missing information.
                - Estimate delivery price only when enough information is available.
                - Explain the estimate briefly.
                - Recommend creating an order in DeliveryOS.
                """.formatted(
                orElseUnknown(details.pickupCity()),
                orElseUnknown(details.destinationCity()),
                orElseUnknown(details.weight()),
                orElseUnknown(details.packageType()),
                message);
    }

    private String orElseUnknown(String value) {
        return value == null ? UNKNOWN : value;
    }
}
