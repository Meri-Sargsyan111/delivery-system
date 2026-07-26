package com.example.aiservice.extraction;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls pickup city / destination city / weight / package type out of a customer's raw
 * message using plain regex and keyword matching - no AI model involved, so the result
 * is deterministic and free. Used to pre-fill a "Known information" block in the prompt
 * sent to Ollama (see AiChatServiceImpl) so the assistant never re-asks for something
 * already stated.
 * <p>
 * Deliberately conservative: a field is only ever set when there's a specific, direct
 * match. Anything ambiguous is left {@code null} (rendered as "Unknown" downstream)
 * rather than guessed, matching the same "don't invent values" rule given to the AI.
 */
@Component
public class DeliveryDetailsExtractor {

    /**
     * Armenian cities/towns recognized for pickup/destination detection. Not
     * exhaustive - covers the country's major population centers, which is enough for
     * DeliveryOS's current natural-language delivery requests.
     */
    private static final List<String> KNOWN_CITIES = List.of(
            "Yerevan", "Gyumri", "Vanadzor", "Vagharshapat", "Echmiadzin", "Hrazdan",
            "Abovyan", "Kapan", "Armavir", "Gavar", "Artashat", "Ijevan", "Dilijan",
            "Charentsavan", "Sevan", "Goris", "Stepanavan", "Alaverdi", "Ashtarak",
            "Metsamor", "Sisian", "Masis", "Ararat", "Vardenis", "Byureghavan",
            "Spitak", "Yeghegnadzor", "Meghri", "Tashir", "Berd", "Chambarak"
    );

    private static final String CITY_ALTERNATION = String.join("|", KNOWN_CITIES);

    private static final Pattern CITY_MENTION =
            Pattern.compile("\\b(" + CITY_ALTERNATION + ")\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern FROM_TO =
            Pattern.compile("\\bfrom\\s+(" + CITY_ALTERNATION + ")\\b.*?\\bto\\s+(" + CITY_ALTERNATION + ")\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern TO_FROM =
            Pattern.compile("\\bto\\s+(" + CITY_ALTERNATION + ")\\b.*?\\bfrom\\s+(" + CITY_ALTERNATION + ")\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Matches a number (optionally decimal) immediately followed by a weight unit. */
    private static final Pattern WEIGHT =
            Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kg|kilograms?|g|grams?)\\b", Pattern.CASE_INSENSITIVE);

    /**
     * Specific package-type words. Deliberately excludes generic filler like "package"
     * or "item" itself, which name nothing about what's being shipped.
     */
    private static final Pattern PACKAGE_TYPE = Pattern.compile(
            "\\b(documents?|parcels?|boxes?|electronics?|groceries|clothes|clothing|"
                    + "furniture|fragile\\s+items?|gifts?|letters?|envelopes?|medicines?|"
                    + "phones?|laptops?|computers?|appliances?|food)\\b",
            Pattern.CASE_INSENSITIVE);

    public ExtractedDeliveryDetails extract(String message) {
        if (message == null || message.isBlank()) {
            return new ExtractedDeliveryDetails(null, null, null, null);
        }

        String[] cities = extractCities(message);
        String weight = extractWeight(message);
        String packageType = extractPackageType(message);

        return new ExtractedDeliveryDetails(cities[0], cities[1], weight, packageType);
    }

    /** @return a 2-element array: [pickupCity, destinationCity], either possibly null. */
    private String[] extractCities(String message) {
        Matcher fromTo = FROM_TO.matcher(message);
        if (fromTo.find()) {
            return new String[]{fromTo.group(1), fromTo.group(2)};
        }

        Matcher toFrom = TO_FROM.matcher(message);
        if (toFrom.find()) {
            return new String[]{toFrom.group(2), toFrom.group(1)};
        }

        Set<String> distinctInOrder = new LinkedHashSet<>();
        Matcher mentions = CITY_MENTION.matcher(message);
        while (mentions.find() && distinctInOrder.size() < 2) {
            distinctInOrder.add(mentions.group(1));
        }

        if (distinctInOrder.size() < 2) {
            return new String[]{null, null};
        }
        Iterator<String> it = distinctInOrder.iterator();
        return new String[]{it.next(), it.next()};
    }

    private String extractWeight(String message) {
        Matcher matcher = WEIGHT.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        String unit = matcher.group(2).toLowerCase();
        String normalizedUnit = unit.startsWith("kg") || unit.startsWith("kilo") ? "kg" : "g";
        return matcher.group(1) + " " + normalizedUnit;
    }

    private String extractPackageType(String message) {
        Matcher matcher = PACKAGE_TYPE.matcher(message);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1).toLowerCase();
    }
}
