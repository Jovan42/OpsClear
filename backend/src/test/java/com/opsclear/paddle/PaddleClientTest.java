package com.opsclear.paddle;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit-tests the actual HTTP request shape {@link PaddleClient} builds — request
 * method, URI, headers, body — using {@link MockRestServiceServer} instead of a real
 * network call. JOB-180: with every integration test now mocking {@code PaddleClient}
 * wholesale (to stop hitting Paddle's real sandbox and its rate limits), this class's
 * own implementation stopped being exercised anywhere at all — this closes that gap
 * without reintroducing a live dependency.
 */
@DisplayName("PaddleClient")
class PaddleClientTest {

    private static final String BASE_URL = "https://sandbox-api.paddle.com";
    private static final String API_KEY = "test-api-key";

    private MockRestServiceServer server;
    private PaddleClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PaddleClient(builder, BASE_URL, API_KEY);
    }

    @Test
    @DisplayName("createCustomer posts email/name and maps the response")
    void createCustomer_shouldPostBody_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/customers"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer " + API_KEY))
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andRespond(withSuccess(
                        """
                        {"data": {"id": "ctm_123", "email": "owner@example.com"}}
                        """, MediaType.APPLICATION_JSON));

        PaddleCustomer result = client.createCustomer("owner@example.com", "Acme Corp");

        assertThat(result.id()).isEqualTo("ctm_123");
        assertThat(result.email()).isEqualTo("owner@example.com");
        server.verify();
    }

    @Test
    @DisplayName("updateSubscriptionItems patches items/proration mode and maps the response")
    void updateSubscriptionItems_shouldPatchBody_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/subscriptions/sub_123"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.proration_billing_mode").value("prorated_immediately"))
                .andExpect(jsonPath("$.items[0].price_id").value("pri_123"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andRespond(withSuccess(
                        """
                        {"data": {"id": "sub_123", "status": "active", "customer_id": "ctm_123"}}
                        """, MediaType.APPLICATION_JSON));

        PaddleSubscription result = client.updateSubscriptionItems(
                "sub_123", List.of(new PaddleSubscriptionItem("pri_123", 1)), "prorated_immediately");

        assertThat(result.id()).isEqualTo("sub_123");
        assertThat(result.status()).isEqualTo("active");
        server.verify();
    }

    @Test
    @DisplayName("previewUpdateSubscriptionItems patches the preview endpoint and maps the response")
    void previewUpdateSubscriptionItems_shouldPatchBody_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/subscriptions/sub_123/preview"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withSuccess(
                        """
                        {"data": {"current_billing_period": {"starts_at": "2026-08-01T00:00:00Z",
                        "ends_at": "2026-09-01T00:00:00Z"}, "immediate_transaction": null}}
                        """, MediaType.APPLICATION_JSON));

        PaddleSubscriptionPreview result = client.previewUpdateSubscriptionItems(
                "sub_123", List.of(new PaddleSubscriptionItem("pri_123", 1)), "full_next_billing_period");

        assertThat(result.currentBillingPeriod().startsAt()).isNotNull();
        assertThat(result.immediateTransaction()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("createProduct posts name/tax_category and maps the response")
    void createProduct_shouldPostBody_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/products"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.name").value("Pro Tier"))
                .andExpect(jsonPath("$.tax_category").value("saas"))
                .andRespond(withSuccess(
                        """
                        {"data": {"id": "pro_123", "name": "Pro Tier"}}
                        """, MediaType.APPLICATION_JSON));

        PaddleProduct result = client.createProduct("Pro Tier");

        assertThat(result.id()).isEqualTo("pro_123");
        server.verify();
    }

    @Test
    @DisplayName("createPrice posts the price shape (billing_cycle/unit_price/quantity) and maps the response")
    void createPrice_shouldPostBody_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/prices"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.product_id").value("pro_123"))
                .andExpect(jsonPath("$.billing_cycle.interval").value("month"))
                .andExpect(jsonPath("$.unit_price.amount").value("3900"))
                .andExpect(jsonPath("$.unit_price.currency_code").value("EUR"))
                .andExpect(jsonPath("$.quantity.minimum").value(1))
                .andExpect(jsonPath("$.quantity.maximum").value(1))
                .andRespond(withSuccess(
                        """
                        {"data": {"id": "pri_123", "status": "active"}}
                        """, MediaType.APPLICATION_JSON));

        PaddlePrice result = client.createPrice("pro_123", "Pro Tier - Monthly", "month", "3900", "EUR");

        assertThat(result.id()).isEqualTo("pri_123");
        assertThat(result.status()).isEqualTo("active");
        server.verify();
    }

    @Test
    @DisplayName("archivePrice patches status=archived with no response body expected")
    void archivePrice_shouldPatchStatus() {
        server.expect(requestTo(BASE_URL + "/prices/pri_123"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.status").value("archived"))
                .andRespond(withSuccess());

        client.archivePrice("pri_123");

        server.verify();
    }

    @Test
    @DisplayName("listBillingHistory gets transactions for the customer and maps the response list")
    void listBillingHistory_shouldGetTransactions_andMapResponse() {
        server.expect(requestTo(BASE_URL
                        + "/transactions?customer_id=ctm_123&order_by=billed_at%5BDESC%5D&per_page=20"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"data": [{"id": "txn_123", "status": "completed", "items": [],
                        "billed_at": "2026-08-01T00:00:00Z", "currency_code": "EUR",
                        "details": {"totals": {"total": "3900"}}}]}
                        """, MediaType.APPLICATION_JSON));

        List<PaddleTransaction> result = client.listBillingHistory("ctm_123");

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("txn_123");
        assertThat(result.getFirst().details().totals().total()).isEqualTo("3900");
        server.verify();
    }

    @Test
    @DisplayName("cancelSubscription posts effective_from=next_billing_period and maps the response")
    void cancelSubscription_shouldPostBody_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/subscriptions/sub_123/cancel"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.effective_from").value("next_billing_period"))
                .andRespond(withSuccess(
                        """
                        {"data": {"id": "sub_123", "status": "canceled"}}
                        """, MediaType.APPLICATION_JSON));

        PaddleSubscription result = client.cancelSubscription("sub_123");

        assertThat(result.status()).isEqualTo("canceled");
        server.verify();
    }

    @Test
    @DisplayName("removeScheduledCancellation sends a literal null scheduled_change and maps the response")
    void removeScheduledCancellation_shouldPatchNullScheduledChange_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/subscriptions/sub_123"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(content().json("""
                        {"scheduled_change": null}
                        """))
                .andRespond(withSuccess(
                        """
                        {"data": {"id": "sub_123", "status": "active"}}
                        """, MediaType.APPLICATION_JSON));

        PaddleSubscription result = client.removeScheduledCancellation("sub_123");

        assertThat(result.status()).isEqualTo("active");
        server.verify();
    }

    @Test
    @DisplayName("getUpdatePaymentMethodTransaction gets the transaction and maps the response")
    void getUpdatePaymentMethodTransaction_shouldGetTransaction_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/subscriptions/sub_123/update-payment-method-transaction"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        """
                        {"data": {"id": "txn_123", "status": "draft", "items": []}}
                        """, MediaType.APPLICATION_JSON));

        PaddleTransaction result = client.getUpdatePaymentMethodTransaction("sub_123");

        assertThat(result.id()).isEqualTo("txn_123");
        server.verify();
    }

    @Test
    @DisplayName("createOneTimeDiscount posts a checkout-disabled flat discount capped to one recurring interval")
    void createOneTimeDiscount_shouldPostBody_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/discounts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.type").value("flat"))
                .andExpect(jsonPath("$.amount").value("2900"))
                .andExpect(jsonPath("$.currency_code").value("EUR"))
                .andExpect(jsonPath("$.enabled_for_checkout").value(false))
                .andExpect(jsonPath("$.recur").value(true))
                .andExpect(jsonPath("$.maximum_recurring_intervals").value(1))
                .andExpect(jsonPath("$.usage_limit").value(1))
                .andRespond(withSuccess(
                        """
                        {"data": {"id": "dsc_123"}}
                        """, MediaType.APPLICATION_JSON));

        PaddleDiscount result = client.createOneTimeDiscount("2900", "EUR", "OpsClear credit: Goodwill");

        assertThat(result.id()).isEqualTo("dsc_123");
        server.verify();
    }

    @Test
    @DisplayName("attachDiscountToSubscription patches the discount reference and maps the response")
    void attachDiscountToSubscription_shouldPatchBody_andMapResponse() {
        server.expect(requestTo(BASE_URL + "/subscriptions/sub_123"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(jsonPath("$.discount.id").value("dsc_123"))
                .andExpect(jsonPath("$.discount.effective_from").value("immediately"))
                .andRespond(withSuccess(
                        """
                        {"data": {"id": "sub_123", "status": "active"}}
                        """, MediaType.APPLICATION_JSON));

        PaddleSubscription result = client.attachDiscountToSubscription("sub_123", "dsc_123");

        assertThat(result.id()).isEqualTo("sub_123");
        server.verify();
    }
}
