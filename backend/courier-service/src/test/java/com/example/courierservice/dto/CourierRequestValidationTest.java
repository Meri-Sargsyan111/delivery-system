package com.example.courierservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CourierRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    void createCourierRequest_withBlankName_failsValidation() {
        Set<ConstraintViolation<CreateCourierRequest>> violations =
                validator.validate(new CreateCourierRequest("", null, null));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void createCourierRequest_withNameOver100Characters_failsValidation() {
        String tooLong = "a".repeat(101);

        Set<ConstraintViolation<CreateCourierRequest>> violations =
                validator.validate(new CreateCourierRequest(tooLong, null, null));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void createCourierRequest_withValidName_passesValidation() {
        Set<ConstraintViolation<CreateCourierRequest>> violations =
                validator.validate(new CreateCourierRequest("Alice Johnson", null, null));

        assertThat(violations).isEmpty();
    }

    @Test
    void createCourierRequest_withoutPhotoUrl_passesValidation() {
        Set<ConstraintViolation<CreateCourierRequest>> violations =
                validator.validate(new CreateCourierRequest("Alice Johnson", null, null));

        assertThat(violations).isEmpty();
    }

    @Test
    void createCourierRequest_withValidHttpPhotoUrl_passesValidation() {
        Set<ConstraintViolation<CreateCourierRequest>> violations =
                validator.validate(new CreateCourierRequest("Alice Johnson", "http://example.com/photo.jpg", null));

        assertThat(violations).isEmpty();
    }

    @Test
    void createCourierRequest_withValidHttpsPhotoUrl_passesValidation() {
        Set<ConstraintViolation<CreateCourierRequest>> violations =
                validator.validate(new CreateCourierRequest("Alice Johnson", "https://example.com/photo.jpg", null));

        assertThat(violations).isEmpty();
    }

    @Test
    void createCourierRequest_withInvalidPhotoUrl_failsValidation() {
        Set<ConstraintViolation<CreateCourierRequest>> violations =
                validator.validate(new CreateCourierRequest("Alice Johnson", "not-a-url", null));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void createCourierRequest_withPhotoUrlOver500Characters_failsValidation() {
        String tooLong = "https://example.com/" + "a".repeat(500);

        Set<ConstraintViolation<CreateCourierRequest>> violations =
                validator.validate(new CreateCourierRequest("Alice Johnson", tooLong, null));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void rateOrderRequest_withValueBelowOne_failsValidation() {
        Set<ConstraintViolation<RateOrderRequest>> violations =
                validator.validate(new RateOrderRequest(0));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void rateOrderRequest_withValueAboveFive_failsValidation() {
        Set<ConstraintViolation<RateOrderRequest>> violations =
                validator.validate(new RateOrderRequest(6));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void rateOrderRequest_withValueInRange_passesValidation() {
        for (int value = 1; value <= 5; value++) {
            Set<ConstraintViolation<RateOrderRequest>> violations =
                    validator.validate(new RateOrderRequest(value));
            assertThat(violations).isEmpty();
        }
    }
}