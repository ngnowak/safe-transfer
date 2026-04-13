package com.nn.safetransfer.e2e;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.common.metrics.MetricName;
import com.nn.safetransfer.common.metrics.MetricTag;
import com.nn.safetransfer.common.metrics.TransferMetricOutcome;
import com.nn.safetransfer.transfer.domain.TransferStatus;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

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
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @BeforeAll
    static void shouldReachApplication() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void shouldExecuteTransferFlowAndRecordMetrics() throws Exception {
        var tenantId = UUID.randomUUID();
        var sourceWallet = createWallet(tenantId, UUID.randomUUID(), CurrencyCode.EUR);
        var destinationWallet = createWallet(tenantId, UUID.randomUUID(), CurrencyCode.EUR);

        deposit(tenantId, sourceWallet.walletId(), new BigDecimal("100.00"), CurrencyCode.EUR, "e2e deposit");

        var successCounterBefore = metricCount(MetricName.TRANSFER_CREATED, metricTag(MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS));
        var failureCounterBefore = metricCount(MetricName.TRANSFER_CREATED, metricTag(MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS));

        var successfulTransfer = post(
                "/api/v1/tenants/%s/transfers".formatted(tenantId),
                Map.of(
                        "sourceWalletId", sourceWallet.walletId(),
                        "destinationWalletId", destinationWallet.walletId(),
                        "amount", new BigDecimal("25.00"),
                        "currency", CurrencyCode.EUR.name(),
                        "reference", "e2e success"
                ),
                Map.of(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
        );

        assertThat(successfulTransfer.statusCode()).isEqualTo(HttpStatus.CREATED.value());
        var successBody = OBJECT_MAPPER.readTree(successfulTransfer.body());
        assertThat(successBody.path("transferId").asText()).isNotBlank();
        assertThat(successBody.path("status").asText()).isEqualTo(TransferStatus.COMPLETED.name());

        var failedTransfer = post(
                "/api/v1/tenants/%s/transfers".formatted(tenantId),
                Map.of(
                        "sourceWalletId", sourceWallet.walletId(),
                        "destinationWalletId", destinationWallet.walletId(),
                        "amount", new BigDecimal("1000.00"),
                        "currency", CurrencyCode.EUR.name(),
                        "reference", "e2e failure"
                ),
                Map.of(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
        );

        assertThat(failedTransfer.statusCode()).isEqualTo(HttpStatus.CONFLICT.value());
        var failedBody = OBJECT_MAPPER.readTree(failedTransfer.body());
        assertThat(failedBody.path("errorMessage").asText()).contains("insufficient funds");

        assertThat(metricCount(MetricName.TRANSFER_CREATED, metricTag(MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS)))
                .isEqualTo(successCounterBefore + 1.0d);
        assertThat(metricCount(MetricName.TRANSFER_CREATED, metricTag(MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS)))
                .isEqualTo(failureCounterBefore + 1.0d);
    }

    private WalletResponse createWallet(UUID tenantId, UUID customerId, CurrencyCode currency) throws Exception {
        var response = post(
                "/api/v1/tenants/%s/wallets".formatted(tenantId),
                Map.of("customerId", customerId, "currency", currency.name()),
                Map.of()
        );

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
        return OBJECT_MAPPER.readValue(response.body(), WalletResponse.class);
    }

    private void deposit(UUID tenantId, String walletId, BigDecimal amount, CurrencyCode currency, String reference) throws Exception {
        var response = post(
                "/api/v1/tenants/%s/wallets/%s/deposits".formatted(tenantId, walletId),
                Map.of("amount", amount, "currency", currency.name(), "reference", reference),
                Map.of()
        );

        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    private double metricCount(MetricName metricName, String tag) throws Exception {
        var encodedTag = URLEncoder.encode(tag, UTF_8);
        var response = get("/actuator/metrics/%s?tag=%s".formatted(metricName.getValue(), encodedTag));

        if (response.statusCode() == HttpStatus.NOT_FOUND.value()) {
            return 0.0d;
        }

        assertThat(response.statusCode())
                .withFailMessage("Expected metric endpoint to return 200 but got %s. Body: %s", response.statusCode(), response.body())
                .isEqualTo(HttpStatus.OK.value());
        JsonNode body = OBJECT_MAPPER.readTree(response.body());
        return body.path("measurements").get(0).path("value").asDouble();
    }

    private String metricTag(MetricTag tag, TransferMetricOutcome outcome) {
        return "%s:%s".formatted(tag.getValue(), outcome.getTagValue());
    }

    private static HttpResponse<String> get(String path) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, Object body, Map<String, String> headers) throws IOException, InterruptedException {
        var requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(body)));

        headers.forEach(requestBuilder::header);

        return HTTP_CLIENT.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WalletResponse(String walletId) {
    }
}
