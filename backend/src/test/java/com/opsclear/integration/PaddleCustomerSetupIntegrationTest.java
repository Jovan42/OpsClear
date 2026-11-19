package com.opsclear.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsclear.model.OrganisationModel;
import com.opsclear.model.UserModel;
import com.opsclear.paddle.PaddleClient;
import com.opsclear.repository.OrgSubscriptionRepository;
import com.opsclear.repository.OrganisationRepository;
import com.opsclear.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.ResponseActions;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * JOB-396: {@code POST /subscription/paddle} through the real {@link PaddleClient}, with
 * Paddle's HTTP API stubbed by {@link MockRestServiceServer} instead of mocking the client,
 * so the request building, response mapping and error mapping all run. Paddle answers
 * {@code POST /customers} with 409 customer_already_exists when the email already has a
 * customer; that used to surface as a bare 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Paddle customer setup against a stubbed Paddle API")
class PaddleCustomerSetupIntegrationTest {

    private static final String PADDLE_URL = "https://paddle.test";
    private static final String OWNER_EMAIL = "ana+qa@example.com";
    private static final String ENCODED_OWNER_EMAIL = "ana%2Bqa%40example.com";
    private static final String SETUP_FAILED =
            "Couldn't set up billing with Paddle. Please try again in a few minutes.";
    // The body Paddle documents for POST /customers with an email that already has a customer.
    private static final String CUSTOMER_ALREADY_EXISTS = """
            {"error": {"type": "request_error", "code": "customer_already_exists",
             "detail": "Customer email conflicts with existing customer ID ctm_existing."}}
            """;

    @TestConfiguration
    static class StubbedPaddleApi {

        private final RestClient.Builder builder = RestClient.builder();

        @Bean
        MockRestServiceServer paddleApi() {
            return MockRestServiceServer.bindTo(builder).build();
        }

        // Built after paddleApi() binds the builder, so its RestClient talks to the stub.
        @Bean
        @Primary
        PaddleClient stubbedPaddleClient(MockRestServiceServer paddleApi) {
            return new PaddleClient(builder, PADDLE_URL, "test-api-key");
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MockRestServiceServer paddleApi;
    @Autowired private OrgSubscriptionRepository subscriptionRepository;
    @Autowired private OrganisationRepository organisationRepository;
    @Autowired private UserRepository userRepository;

    private UUID ownerId;
    private UUID orgId;

    @BeforeEach
    void setUp() throws Exception {
        paddleApi.reset();
        subscriptionRepository.deleteAll();
        organisationRepository.deleteAll();
        userRepository.deleteAll();

        ownerId = UUID.randomUUID();
        userRepository.save(UserModel.builder().id(ownerId).email(OWNER_EMAIL).name("Ana").build());
        String response = mockMvc.perform(post(ApiPaths.ORGANISATIONS)
                        .with(jwt().jwt(j -> j.subject(ownerId.toString())
                                .claim("email", OWNER_EMAIL).claim("name", "Ana")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Brightline", "slug", "BRL"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        orgId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    @AfterEach
    void verifyPaddleCalls() {
        paddleApi.verify();
    }

    @Test
    @DisplayName("initiate_shouldReturn201_andSaveTheNewCustomer_whenPaddleCreatesIt")
    void initiate_shouldReturn201_andSaveTheNewCustomer_whenPaddleCreatesIt() throws Exception {
        paddleApi.expect(requestTo(PADDLE_URL + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.email").value(OWNER_EMAIL))
                .andExpect(jsonPath("$.name").value("Brightline"))
                .andRespond(withSuccess("""
                        {"data": {"id": "ctm_new", "email": "ana+qa@example.com"}}
                        """, MediaType.APPLICATION_JSON));

        initiate().andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers
                        .jsonPath("$.paddleCustomerId").value("ctm_new"));

        assertThat(organisationRepository.findPaddleCustomerId(orgId)).contains("ctm_new");
    }

    @Test
    @DisplayName("initiate_shouldReturn201_andReuseTheExistingCustomer_whenPaddleSaysEmailAlreadyExists")
    void initiate_shouldReturn201_andReuseTheExistingCustomer_whenPaddleSaysEmailAlreadyExists() throws Exception {
        expectCreateCustomerConflict();
        expectCustomerLookup().andRespond(withSuccess("""
                {"data": [{"id": "ctm_existing", "email": "ana+qa@example.com"}]}
                """, MediaType.APPLICATION_JSON));

        initiate().andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers
                        .jsonPath("$.paddleCustomerId").value("ctm_existing"));

        assertThat(organisationRepository.findPaddleCustomerId(orgId)).contains("ctm_existing");
    }

    @Test
    @DisplayName("initiate_shouldReturn409_whenTheExistingCustomerBelongsToAnotherOrg")
    void initiate_shouldReturn409_whenTheExistingCustomerBelongsToAnotherOrg() throws Exception {
        // One org per user, so the realistic "another org" is the owner's own earlier,
        // since soft-deleted org: its paddle_customer_id stays, and so does its Paddle side.
        OrganisationModel earlier = organisationRepository.save(
                OrganisationModel.builder().name("Earlier").slug("EAR").createdBy(ownerId).build());
        earlier.softDelete();
        organisationRepository.save(earlier);
        organisationRepository.updatePaddleCustomerId(earlier.getId(), "ctm_existing");
        expectCreateCustomerConflict();
        expectCustomerLookup().andRespond(withSuccess("""
                {"data": [{"id": "ctm_existing", "email": "ana+qa@example.com"}]}
                """, MediaType.APPLICATION_JSON));

        initiate().andExpect(status().isConflict())
                .andExpect(message("Your email is already the billing contact for another organisation's "
                        + "subscription, and Paddle allows one billing customer per email"));

        assertThat(organisationRepository.findPaddleCustomerId(orgId)).isEmpty();
    }

    @Test
    @DisplayName("initiate_shouldReturn502_whenPaddleSaysEmailAlreadyExists_butTheLookupFindsNoCustomer")
    void initiate_shouldReturn502_whenPaddleSaysEmailAlreadyExists_butTheLookupFindsNoCustomer() throws Exception {
        expectCreateCustomerConflict();
        expectCustomerLookup().andRespond(withSuccess("""
                {"data": []}
                """, MediaType.APPLICATION_JSON));

        initiate().andExpect(status().isBadGateway()).andExpect(message(SETUP_FAILED));

        assertThat(organisationRepository.findPaddleCustomerId(orgId)).isEmpty();
    }

    @Test
    @DisplayName("initiate_shouldReturn502_whenTheLookupResponseHasNoData")
    void initiate_shouldReturn502_whenTheLookupResponseHasNoData() throws Exception {
        expectCreateCustomerConflict();
        expectCustomerLookup().andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        initiate().andExpect(status().isBadGateway()).andExpect(message(SETUP_FAILED));
    }

    @Test
    @DisplayName("initiate_shouldReturn502_whenTheLookupResponseIsEmpty")
    void initiate_shouldReturn502_whenTheLookupResponseIsEmpty() throws Exception {
        expectCreateCustomerConflict();
        expectCustomerLookup().andRespond(withSuccess());

        initiate().andExpect(status().isBadGateway()).andExpect(message(SETUP_FAILED));
    }

    @Test
    @DisplayName("initiate_shouldReturn502_whenTheExistingCustomerLookupFails")
    void initiate_shouldReturn502_whenTheExistingCustomerLookupFails() throws Exception {
        expectCreateCustomerConflict();
        expectCustomerLookup().andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        initiate().andExpect(status().isBadGateway()).andExpect(message(SETUP_FAILED));
    }

    @Test
    @DisplayName("initiate_shouldReturn502_notABare500_whenPaddleRejectsCustomerCreation")
    void initiate_shouldReturn502_notABare500_whenPaddleRejectsCustomerCreation() throws Exception {
        paddleApi.expect(requestTo(PADDLE_URL + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        initiate().andExpect(status().isBadGateway()).andExpect(message(SETUP_FAILED));

        assertThat(organisationRepository.findPaddleCustomerId(orgId)).isEmpty();
    }

    private ResultActions initiate() throws Exception {
        return mockMvc.perform(post(ApiPaths.paddleSubscription(orgId))
                .with(jwt().jwt(j -> j.subject(ownerId.toString()).claim("email", OWNER_EMAIL))));
    }

    private void expectCreateCustomerConflict() {
        paddleApi.expect(requestTo(PADDLE_URL + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(CUSTOMER_ALREADY_EXISTS));
    }

    private ResponseActions expectCustomerLookup() {
        return paddleApi.expect(requestTo(PADDLE_URL + "/customers?email=" + ENCODED_OWNER_EMAIL))
                .andExpect(method(HttpMethod.GET));
    }

    private static ResultMatcher message(String expected) {
        return MockMvcResultMatchers.jsonPath("$.message")
                .value(expected);
    }
}
