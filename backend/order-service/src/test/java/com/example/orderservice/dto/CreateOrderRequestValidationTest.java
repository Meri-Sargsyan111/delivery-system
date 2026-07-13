package com.example.orderservice.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestValidationTest {

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
    void createOrderRequest_withInternationalPhoneFormat_passesValidation() {
        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(
                new CreateOrderRequest("John", "From St", "To St", "+37499123456"));

        assertThat(violations).isEmpty();
    }

    @Test
    void createOrderRequest_withLocalPhoneFormat_passesValidation() {
        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(
                new CreateOrderRequest("John", "From St", "To St", "099123456"));

        assertThat(violations).isEmpty();
    }

    @Test
    void createOrderRequest_withSpacesHyphensAndParentheses_passesValidation() {
        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(
                new CreateOrderRequest("John", "From St", "To St", "+1 202 555 0123"));

        assertThat(violations).isEmpty();

        Set<ConstraintViolation<CreateOrderRequest>> violationsWithHyphens = validator.validate(
                new CreateOrderRequest("John", "From St", "To St", "099-123-456"));

        assertThat(violationsWithHyphens).isEmpty();

        Set<ConstraintViolation<CreateOrderRequest>> violationsWithParens = validator.validate(
                new CreateOrderRequest("John", "From St", "To St", "(099) 123 456"));

        assertThat(violationsWithParens).isEmpty();
    }

    @Test
    void createOrderRequest_withBlankPhone_failsValidation() {
        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(
                new CreateOrderRequest("John", "From St", "To St", "   "));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void createOrderRequest_withClearlyInvalidPhoneText_failsValidation() {
        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(
                new CreateOrderRequest("John", "From St", "To St", "call me maybe"));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void createOrderRequest_withTooLongPhone_failsValidation() {
        String tooLong = "+" + "1".repeat(30);

        Set<ConstraintViolation<CreateOrderRequest>> violations = validator.validate(
                new CreateOrderRequest("John", "From St", "To St", tooLong));

        assertThat(violations).isNotEmpty();
    }
}