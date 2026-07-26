package com.example.orderservice.service;

import com.example.orderservice.client.AuthServiceClient;
import com.example.orderservice.client.CustomerLookupResult;
import com.example.orderservice.dto.CreateOrderRequest;
import com.example.orderservice.exception.EntityNotFoundException;
import com.example.orderservice.security.CurrentUser;
import com.example.orderservice.service.impl.OrderCustomerResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The security-critical piece of the create-order flow: which customer a new order
 * is attributed to. See business rules - a CUSTOMER can never create an order for
 * anyone but themselves, and an ADMIN must pick an existing customer explicitly.
 */
@ExtendWith(MockitoExtension.class)
class OrderCustomerResolverTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();

    @Mock private AuthServiceClient authServiceClient;
    @Mock private CurrentUser currentUser;

    private OrderCustomerResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new OrderCustomerResolver(authServiceClient, currentUser);
    }

    @Test
    void resolve_asCustomer_ignoresProvidedCustomerIdAndUsesAuthenticatedIdentity() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.getUserId()).thenReturn(CUSTOMER_ID);
        CreateOrderRequest request = new CreateOrderRequest(OTHER_CUSTOMER_ID, "From St", "To St", null, null, null);
        CustomerLookupResult expected = new CustomerLookupResult(CUSTOMER_ID, "John", "Doe", "+37499123456");
        when(authServiceClient.getCustomerById(CUSTOMER_ID)).thenReturn(expected);

        CustomerLookupResult result = resolver.resolve(request);

        assertThat(result).isEqualTo(expected);
        verify(authServiceClient).getCustomerById(CUSTOMER_ID);
        verify(authServiceClient, never()).getCustomerById(OTHER_CUSTOMER_ID);
    }

    @Test
    void resolve_asCustomer_withNoCustomerIdInRequest_stillResolvesOwnIdentity() {
        when(currentUser.isAdmin()).thenReturn(false);
        when(currentUser.getUserId()).thenReturn(CUSTOMER_ID);
        CreateOrderRequest request = new CreateOrderRequest(null, "From St", "To St", null, null, null);
        CustomerLookupResult expected = new CustomerLookupResult(CUSTOMER_ID, "John", "Doe", "+37499123456");
        when(authServiceClient.getCustomerById(CUSTOMER_ID)).thenReturn(expected);

        CustomerLookupResult result = resolver.resolve(request);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void resolve_asAdmin_resolvesThePickedCustomerId() {
        when(currentUser.isAdmin()).thenReturn(true);
        CreateOrderRequest request = new CreateOrderRequest(OTHER_CUSTOMER_ID, "From St", "To St", null, null, null);
        CustomerLookupResult expected = new CustomerLookupResult(OTHER_CUSTOMER_ID, "Jane", "Smith", "+37499123456");
        when(authServiceClient.getCustomerById(OTHER_CUSTOMER_ID)).thenReturn(expected);

        CustomerLookupResult result = resolver.resolve(request);

        assertThat(result).isEqualTo(expected);
        verify(currentUser, never()).getUserId();
    }

    @Test
    void resolve_asAdmin_withoutCustomerId_throwsIllegalArgumentExceptionAndSkipsLookup() {
        when(currentUser.isAdmin()).thenReturn(true);
        CreateOrderRequest request = new CreateOrderRequest(null, "From St", "To St", null, null, null);

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(authServiceClient);
    }

    @Test
    void resolve_asAdmin_whenPickedCustomerDoesNotExist_propagatesEntityNotFoundException() {
        when(currentUser.isAdmin()).thenReturn(true);
        CreateOrderRequest request = new CreateOrderRequest(OTHER_CUSTOMER_ID, "From St", "To St", null, null, null);
        when(authServiceClient.getCustomerById(OTHER_CUSTOMER_ID))
                .thenThrow(new EntityNotFoundException("Customer not found with id: " + OTHER_CUSTOMER_ID));

        assertThatThrownBy(() -> resolver.resolve(request))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
