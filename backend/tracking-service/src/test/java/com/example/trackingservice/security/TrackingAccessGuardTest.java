package com.example.trackingservice.security;

import com.example.trackingservice.client.OrderServiceClient;
import com.example.trackingservice.client.RemoteOrderView;
import com.example.trackingservice.entity.OrderOwnership;
import com.example.trackingservice.repository.OrderOwnershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackingAccessGuardTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID OTHER_CUSTOMER_ID = UUID.randomUUID();
    private static final UUID COURIER_USER_ID = UUID.randomUUID();

    @Mock private OrderOwnershipRepository orderOwnershipRepository;
    @Mock private OrderServiceClient orderServiceClient;
    @Mock private CurrentUser currentUser;

    @InjectMocks private TrackingAccessGuard trackingAccessGuard;

    @BeforeEach
    void setUp() {
        asAdmin();
    }

    private void asAdmin() {
        lenient().when(currentUser.isAdmin()).thenReturn(true);
        lenient().when(currentUser.isCustomer()).thenReturn(false);
        lenient().when(currentUser.isCourier()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(ADMIN_ID);
    }

    private void asCustomer(UUID userId) {
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.isCustomer()).thenReturn(true);
        lenient().when(currentUser.isCourier()).thenReturn(false);
        lenient().when(currentUser.getUserId()).thenReturn(userId);
    }

    private void asCourier(UUID userId) {
        lenient().when(currentUser.isAdmin()).thenReturn(false);
        lenient().when(currentUser.isCustomer()).thenReturn(false);
        lenient().when(currentUser.isCourier()).thenReturn(true);
        lenient().when(currentUser.getUserId()).thenReturn(userId);
    }

    @Test
    void requireAccess_asAdmin_neverConsultsOwnership() {
        assertThatCode(() -> trackingAccessGuard.requireAccess(42L)).doesNotThrowAnyException();
        verify(orderOwnershipRepository, org.mockito.Mockito.never()).findById(ArgumentMatchers.any());
    }

    @Test
    void requireAccess_asOwningCustomer_succeeds() {
        asCustomer(CUSTOMER_ID);
        when(orderOwnershipRepository.findById(42L))
                .thenReturn(Optional.of(new OrderOwnership(42L, CUSTOMER_ID, COURIER_USER_ID)));

        assertThatCode(() -> trackingAccessGuard.requireAccess(42L)).doesNotThrowAnyException();
    }

    @Test
    void requireAccess_asUnrelatedCustomer_throwsAccessDenied() {
        asCustomer(OTHER_CUSTOMER_ID);
        when(orderOwnershipRepository.findById(42L))
                .thenReturn(Optional.of(new OrderOwnership(42L, CUSTOMER_ID, COURIER_USER_ID)));
        when(orderServiceClient.getOrder(42L)).thenReturn(null);

        assertThatThrownBy(() -> trackingAccessGuard.requireAccess(42L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireAccess_asAssignedCourier_succeeds() {
        asCourier(COURIER_USER_ID);
        when(orderOwnershipRepository.findById(42L))
                .thenReturn(Optional.of(new OrderOwnership(42L, CUSTOMER_ID, COURIER_USER_ID)));

        assertThatCode(() -> trackingAccessGuard.requireAccess(42L)).doesNotThrowAnyException();
    }

    @Test
    void requireAccess_whenLocalOwnershipMissing_fallsBackToOrderServiceAndSucceeds() {
        asCustomer(CUSTOMER_ID);
        when(orderOwnershipRepository.findById(99L)).thenReturn(Optional.empty());
        when(orderOwnershipRepository.save(ArgumentMatchers.any(OrderOwnership.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderServiceClient.getOrder(99L))
                .thenReturn(new RemoteOrderView(99L, "CREATED", CUSTOMER_ID, null));

        assertThatCode(() -> trackingAccessGuard.requireAccess(99L)).doesNotThrowAnyException();
        verify(orderOwnershipRepository).save(ArgumentMatchers.any(OrderOwnership.class));
    }

    @Test
    void requireAccess_whenOrderServiceFallbackAlsoDoesNotMatch_throwsAccessDenied() {
        asCustomer(OTHER_CUSTOMER_ID);
        when(orderOwnershipRepository.findById(99L)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(99L))
                .thenReturn(new RemoteOrderView(99L, "CREATED", CUSTOMER_ID, null));

        assertThatThrownBy(() -> trackingAccessGuard.requireAccess(99L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void requireAccess_whenNoOwnershipRecordedAnywhere_throwsAccessDenied() {
        asCustomer(CUSTOMER_ID);
        when(orderOwnershipRepository.findById(99L)).thenReturn(Optional.empty());
        when(orderServiceClient.getOrder(99L)).thenReturn(null);

        assertThatThrownBy(() -> trackingAccessGuard.requireAccess(99L))
                .isInstanceOf(AccessDeniedException.class);
    }
}
