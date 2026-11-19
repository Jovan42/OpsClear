package com.opsclear.service;

import com.opsclear.exception.ConflictException;
import com.opsclear.exception.ErrorMessages;
import com.opsclear.exception.PaddleSyncException;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.paddle.PaddleCustomer;
import com.opsclear.repository.OrganisationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaddleCustomerService")
class PaddleCustomerServiceTest {

    private static final String EMAIL = "owner@example.com";
    private static final String NAME = "Acme Corp";

    @Mock private PaddleClient paddleClient;
    @Mock private OrganisationRepository organisationRepository;

    private PaddleCustomerService service;
    private final UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PaddleCustomerService(paddleClient, organisationRepository);
    }

    @Test
    @DisplayName("createOrReuse returns the newly created Paddle customer")
    void createOrReuse_shouldReturnCreatedCustomer() {
        when(paddleClient.createCustomer(EMAIL, NAME)).thenReturn(new PaddleCustomer("ctm_new", EMAIL));

        assertThat(service.createOrReuse(orgId, EMAIL, NAME).id()).isEqualTo("ctm_new");
        verify(paddleClient, never()).findCustomerByEmail(anyString());
    }

    @Test
    @DisplayName("createOrReuse reuses the existing Paddle customer when Paddle says the email already has one")
    void createOrReuse_shouldReuseExistingCustomer_whenPaddleReturns409() {
        when(paddleClient.createCustomer(EMAIL, NAME)).thenThrow(paddleError(HttpStatus.CONFLICT));
        when(paddleClient.findCustomerByEmail(EMAIL)).thenReturn(Optional.of(new PaddleCustomer("ctm_existing", EMAIL)));
        when(organisationRepository.findIdByPaddleCustomerId("ctm_existing")).thenReturn(Optional.empty());

        assertThat(service.createOrReuse(orgId, EMAIL, NAME).id()).isEqualTo("ctm_existing");
    }

    @Test
    @DisplayName("createOrReuse throws ConflictException when the existing Paddle customer is linked to another org")
    void createOrReuse_shouldThrowConflict_whenExistingCustomerLinkedToAnotherOrg() {
        when(paddleClient.createCustomer(EMAIL, NAME)).thenThrow(paddleError(HttpStatus.CONFLICT));
        when(paddleClient.findCustomerByEmail(EMAIL)).thenReturn(Optional.of(new PaddleCustomer("ctm_existing", EMAIL)));
        when(organisationRepository.findIdByPaddleCustomerId("ctm_existing"))
                .thenReturn(Optional.of(UUID.randomUUID()));

        assertThatThrownBy(() -> service.createOrReuse(orgId, EMAIL, NAME))
                .isInstanceOf(ConflictException.class)
                .hasMessage(ErrorMessages.Paddle.CUSTOMER_EMAIL_BELONGS_TO_ANOTHER_ORG);
    }

    @Test
    @DisplayName("createOrReuse throws PaddleSyncException when Paddle says 409 but the email lookup finds nothing")
    void createOrReuse_shouldThrowPaddleSync_whenConflictButLookupEmpty() {
        when(paddleClient.createCustomer(EMAIL, NAME)).thenThrow(paddleError(HttpStatus.CONFLICT));
        when(paddleClient.findCustomerByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createOrReuse(orgId, EMAIL, NAME))
                .isInstanceOf(PaddleSyncException.class)
                .hasMessage(ErrorMessages.Paddle.CUSTOMER_SETUP_FAILED);
    }

    @Test
    @DisplayName("createOrReuse throws PaddleSyncException when the existing-customer lookup fails")
    void createOrReuse_shouldThrowPaddleSync_whenLookupFails() {
        when(paddleClient.createCustomer(EMAIL, NAME)).thenThrow(paddleError(HttpStatus.CONFLICT));
        when(paddleClient.findCustomerByEmail(EMAIL)).thenThrow(new RestClientException("down"));

        assertThatThrownBy(() -> service.createOrReuse(orgId, EMAIL, NAME))
                .isInstanceOf(PaddleSyncException.class)
                .hasMessage(ErrorMessages.Paddle.CUSTOMER_SETUP_FAILED);
    }

    @Test
    @DisplayName("createOrReuse throws PaddleSyncException, not the raw client error, when Paddle rejects creation")
    void createOrReuse_shouldThrowPaddleSync_whenCreateCustomerFails() {
        when(paddleClient.createCustomer(EMAIL, NAME)).thenThrow(paddleError(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> service.createOrReuse(orgId, EMAIL, NAME))
                .isInstanceOf(PaddleSyncException.class)
                .hasMessage(ErrorMessages.Paddle.CUSTOMER_SETUP_FAILED);
        verify(paddleClient, never()).findCustomerByEmail(anyString());
    }

    private static HttpClientErrorException paddleError(HttpStatus status) {
        return HttpClientErrorException.create(
                status, status.getReasonPhrase(), HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }
}
