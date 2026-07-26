package com.example.aiservice.extraction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryDetailsExtractorTest {

    private final DeliveryDetailsExtractor extractor = new DeliveryDetailsExtractor();

    @Test
    void extract_fromToWithWeight_extractsCitiesAndWeightButNotGenericPackageWord() {
        ExtractedDeliveryDetails details =
                extractor.extract("I want to send a 5 kg package from Yerevan to Gyumri.");

        assertThat(details.pickupCity()).isEqualTo("Yerevan");
        assertThat(details.destinationCity()).isEqualTo("Gyumri");
        assertThat(details.weight()).isEqualTo("5 kg");
        assertThat(details.packageType()).isNull();
    }

    @Test
    void extract_fromToWithPackageType_extractsCitiesAndType() {
        ExtractedDeliveryDetails details =
                extractor.extract("I need to send documents from Vanadzor to Dilijan.");

        assertThat(details.pickupCity()).isEqualTo("Vanadzor");
        assertThat(details.destinationCity()).isEqualTo("Dilijan");
        assertThat(details.packageType()).isEqualTo("documents");
        assertThat(details.weight()).isNull();
    }

    @Test
    void extract_withNoDetails_returnsAllNull() {
        ExtractedDeliveryDetails details = extractor.extract("I want to send a package.");

        assertThat(details.pickupCity()).isNull();
        assertThat(details.destinationCity()).isNull();
        assertThat(details.weight()).isNull();
        assertThat(details.packageType()).isNull();
    }

    @Test
    void extract_reversedDirection_toThenFrom_stillAssignsPickupAndDestinationCorrectly() {
        ExtractedDeliveryDetails details =
                extractor.extract("I want to send a package to Gyumri from Yerevan.");

        assertThat(details.pickupCity()).isEqualTo("Yerevan");
        assertThat(details.destinationCity()).isEqualTo("Gyumri");
    }

    @Test
    void extract_isCaseInsensitiveForCityNames() {
        ExtractedDeliveryDetails details = extractor.extract("shipping from YEREVAN to gyumri please");

        assertThat(details.pickupCity()).isEqualToIgnoringCase("Yerevan");
        assertThat(details.destinationCity()).isEqualToIgnoringCase("Gyumri");
    }

    @Test
    void extract_twoCitiesWithoutFromToWording_fallsBackToOrderOfAppearance() {
        ExtractedDeliveryDetails details =
                extractor.extract("Yerevan and Gyumri, I need a delivery between them.");

        assertThat(details.pickupCity()).isEqualTo("Yerevan");
        assertThat(details.destinationCity()).isEqualTo("Gyumri");
    }

    @Test
    void extract_singleCityMention_isTooAmbiguousToAssignEitherRole() {
        ExtractedDeliveryDetails details = extractor.extract("I need to deliver something to Yerevan.");

        assertThat(details.pickupCity()).isNull();
        assertThat(details.destinationCity()).isNull();
    }

    @Test
    void extract_weightInKilograms_variousPhrasings() {
        assertThat(extractor.extract("a 10kg box").weight()).isEqualTo("10 kg");
        assertThat(extractor.extract("weighs about 2.5 kg").weight()).isEqualTo("2.5 kg");
        assertThat(extractor.extract("roughly 3 kilograms").weight()).isEqualTo("3 kg");
    }

    @Test
    void extract_weightInGrams_normalizesUnitToG() {
        ExtractedDeliveryDetails details = extractor.extract("it's only 500 grams");

        assertThat(details.weight()).isEqualTo("500 g");
    }

    @Test
    void extract_packageTypeKeywords_areRecognized() {
        assertThat(extractor.extract("sending some electronics").packageType()).isEqualTo("electronics");
        assertThat(extractor.extract("a fragile item, handle with care").packageType()).isEqualTo("fragile item");
        assertThat(extractor.extract("a parcel for my friend").packageType()).isEqualTo("parcel");
        assertThat(extractor.extract("some food for the party").packageType()).isEqualTo("food");
    }

    @Test
    void extract_blankOrNullMessage_returnsAllNullWithoutThrowing() {
        assertThat(extractor.extract("").pickupCity()).isNull();
        assertThat(extractor.extract("   ").weight()).isNull();
        assertThat(extractor.extract(null).packageType()).isNull();
    }
}
