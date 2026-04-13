package com.nn.safetransfer.e2e;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

import static org.assertj.core.api.Assertions.assertThat;

class TransferFlowE2eTest {

    private static final String BASE_URL = System.getenv().getOrDefault("SAFETRANSFER_BASE_URL", "http://localhost:8080");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @BeforeAll
    static void shouldReachApplication() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void shouldExecuteTransferFlowAndRecordMetrics() throws Exception {
        var tenantId = UUID.randomUUID();
        var sourceWallet = createWallet(tenantId, UUID.randomUUID(), "EUR");
        var destinationWallet = createWallet(tenantId, UUID.randomUUID(), "EUR");

        deposit(tenantId, sourceWallet.walletId(), new BigDecimal("100.00"), "EUR", "e2e deposit");

        var successCounterBefore = metricCount("safetransfer.transfer.created", "outcome:success");
        var failureCounterBefore = metricCount("safetransfer.transfer.created", "outcome:insufficient_funds");

        var successfulTransfer = post(
                "/api/v1/tenants/%s/transfers".formatted(tenantId),
                Map.of(
                        "sourceWalletId", sourceWallet.walletId(),
                        "destinationWalletId", destinationWallet.walletId(),
                        "amount", new BigDecimal("25.00"),
                        "currency", "EUR",
                        "reference", "e2e success"
                ),
                Map.of("Idempotency-Key", UUID.randomUUID().toString())
        );

        assertThat(successfulTransfer.statusCode()).isEqualTo(201);
        var successBody = OBJECT_MAPPER.readTree(successfulTransfer.body());
        assertThat(successBody.path("transferId").asText()).isNotBlank();
        assertThat(successBody.path("status").asText()).isEqualTo("COMPLETED");

        var failedTransfer = post(
                "/api/v1/tenants/%s/transfers".formatted(tenantId),
                Map.of(
                        "sourceWalletId", sourceWallet.walletId(),
                        "destinationWalletId", destinationWallet.walletId(),
                        "amount", new BigDecimal("1000.00"),
                        "currency", "EUR",
                        "reference", "e2e failure"
                ),
                Map.of("Idempotency-Key", UUID.randomUUID().toString())
        );

        assertThat(failedTransfer.statusCode()).isEqualTo(409);
        var failedBody = OBJECT_MAPPER.readTree(failedTransfer.body());
        assertThat(failedBody.path("errorMessage").asText()).contains("insufficient funds");

        assertThat(metricCount("safetransfer.transfer.created", "outcome:success"))
                .isEqualTo(successCounterBefore + 1.0d);
        assertThat(metricCount("safetransfer.transfer.created", "outcome:insufficient_funds"))
                .isEqualTo(failureCounterBefore + 1.0d);
    }

    private WalletResponse createWallet(UUID tenantId, UUID customerId, String currency) throws Exception {
        var response = post(
                "/api/v1/tenants/%s/wallets".formatted(tenantId),
                Map.of("customerId", customerId, "currency", currency),
                Map.of()
        );

        assertThat(response.statusCode()).isEqualTo(200);
        return OBJECT_MAPPER.readValue(response.body(), WalletResponse.class);
    }

    private void deposit(UUID tenantId, String walletId, BigDecimal amount, String currency, String reference) throws Exception {
        var response = post(
                "/api/v1/tenants/%s/wallets/%s/deposits".formatted(tenantId, walletId),
                Map.of("amount", amount, "currency", currency, "reference", reference),
                Map.of()
        );

        assertThat(response.statusCode()).isEqualTo(200);
    }

    private double metricCount(String metricName, String tag) throws Exception {
        var encodedTag = URLEncoder.encode(tag, UTF_8);
        var response = get("/actuator/metrics/%s?tag=%s".formatted(metricName, encodedTag));

        if (response.statusCode() == 404) {
            return 0.0d;
        }

        assertThat(response.statusCode())
                .withFailMessage("Expected metric endpoint to return 200 but got %s. Body: %s", response.statusCode(), response.body())
                .isEqualTo(200);
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        return body.path("measurements").get(0).path("value").asDouble();
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, Object body, Map<String, String> headers) throws IOException, InterruptedException {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)));

        headers.forEach(requestBuilder::header);

        return HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletResponse(String walletId) {
    }
}
