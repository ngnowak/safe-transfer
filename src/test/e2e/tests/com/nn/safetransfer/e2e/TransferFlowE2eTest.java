package com.nn.safetransfer.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.e2e.client.E2eHttpClient;
import com.nn.safetransfer.e2e.client.TransferApiClient;
import com.nn.safetransfer.e2e.client.WalletApiClient;
import com.nn.safetransfer.common.metrics.MetricName;
import com.nn.safetransfer.common.metrics.MetricTag;
import com.nn.safetransfer.common.metrics.TransferMetricOutcome;
import com.nn.safetransfer.transfer.api.dto.CreateTransferRequest;
import com.nn.safetransfer.transfer.domain.TransferStatus;
import com.nn.safetransfer.wallet.api.dto.WalletResponse;
import com.nn.safetransfer.wallet.domain.CurrencyCode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.UUID;

import static com.nn.safetransfer.TestAmounts.ONE_HUNDRED;
import static com.nn.safetransfer.TestAmounts.ONE_THOUSAND;
import static com.nn.safetransfer.TestAmounts.SEVENTY_FIVE;
import static com.nn.safetransfer.TestAmounts.TWENTY_FIVE;
import static com.nn.safetransfer.TestAmounts.ZERO;
import static com.nn.safetransfer.transfer.domain.TransferStatus.COMPLETED;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

class TransferFlowE2eTest {

    private static final String BASE_URL = System.getenv().getOrDefault("SAFETRANSFER_BASE_URL", "http://localhost:8080");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final E2eHttpClient HTTP_CLIENT = new E2eHttpClient(BASE_URL, OBJECT_MAPPER);
    private static final WalletApiClient WALLET_API_CLIENT = new WalletApiClient(HTTP_CLIENT);
    private static final TransferApiClient TRANSFER_API_CLIENT = new TransferApiClient(HTTP_CLIENT);
    public static final String GET_HEALTH_PATH = "/actuator/health";
    public static final String GET_METRICS_PATH = "/actuator/metrics/%s?tag=%s";

    @BeforeAll
    static void shouldReachApplication() throws Exception {
        var response = HTTP_CLIENT.get(GET_HEALTH_PATH);
        assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void shouldExecuteTransferFlowAndRecordMetrics() throws Exception {
        var tenantId = randomUUID();
        var sourceWallet = createWallet(tenantId, randomUUID(), EUR);
        var destinationWallet = createWallet(tenantId, randomUUID(), EUR);

        deposit(tenantId, sourceWallet.walletId(), ONE_HUNDRED, EUR, "e2e deposit");
        assertBalance(tenantId, sourceWallet.walletId(), ONE_HUNDRED);

        var successCounterBefore = metricCount(MetricName.TRANSFER_CREATED, metricTag(MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS));
        var failureCounterBefore = metricCount(MetricName.TRANSFER_CREATED, metricTag(MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS));

        var successfulTransfer = TRANSFER_API_CLIENT.createTransfer(
                tenantId,
                CreateTransferRequest.builder()
                        .sourceWalletId(UUID.fromString(sourceWallet.walletId()))
                        .destinationWalletId(UUID.fromString(destinationWallet.walletId()))
                        .amount(TWENTY_FIVE)
                        .currency(EUR.name())
                        .reference("e2e success")
                        .build(),
                randomUUID().toString()
        );

        assertThat(successfulTransfer.transferId()).isNotBlank();
        assertThat(successfulTransfer.status()).isEqualTo(COMPLETED.name());
        assertTransfer(tenantId, UUID.fromString(successfulTransfer.transferId()), COMPLETED);
        assertBalance(tenantId, sourceWallet.walletId(), SEVENTY_FIVE);
        assertBalance(tenantId, destinationWallet.walletId(), TWENTY_FIVE);

        var failedTransfer = TRANSFER_API_CLIENT.createTransferError(
                tenantId,
                CreateTransferRequest.builder()
                        .sourceWalletId(UUID.fromString(sourceWallet.walletId()))
                        .destinationWalletId(UUID.fromString(destinationWallet.walletId()))
                        .amount(ONE_THOUSAND)
                        .currency(EUR.name())
                        .reference("e2e failure")
                        .build(),
                randomUUID().toString()
        );

        assertThat(failedTransfer.errorMessage()).contains("insufficient funds");

        assertThat(metricCount(MetricName.TRANSFER_CREATED, metricTag(MetricTag.OUTCOME, TransferMetricOutcome.SUCCESS)))
                .isEqualTo(successCounterBefore + 1.0d);
        assertThat(metricCount(MetricName.TRANSFER_CREATED, metricTag(MetricTag.OUTCOME, TransferMetricOutcome.INSUFFICIENT_FUNDS)))
                .isEqualTo(failureCounterBefore + 1.0d);
    }

    @Test
    void shouldCreateTransferAndGetTransferById() throws Exception {
        var tenantId = randomUUID();
        var sourceWallet = createWallet(tenantId, randomUUID(), EUR);
        var destinationWallet = createWallet(tenantId, randomUUID(), EUR);
        deposit(tenantId, sourceWallet.walletId(), ONE_HUNDRED, EUR, "e2e get transfer deposit");

        var createdTransfer = TRANSFER_API_CLIENT.createTransfer(
                tenantId,
                createTransferRequest(sourceWallet, destinationWallet, TWENTY_FIVE, "e2e get transfer"),
                randomUUID().toString()
        );

        var transfer = TRANSFER_API_CLIENT.getTransfer(tenantId, UUID.fromString(createdTransfer.transferId()));

        assertThat(transfer.transferId()).isEqualTo(createdTransfer.transferId());
        assertThat(transfer.tenantId()).isEqualTo(tenantId.toString());
        assertThat(transfer.sourceWalletId()).isEqualTo(sourceWallet.walletId());
        assertThat(transfer.destinationWalletId()).isEqualTo(destinationWallet.walletId());
        assertThat(transfer.amount()).isEqualByComparingTo(TWENTY_FIVE);
        assertThat(transfer.currency()).isEqualTo(EUR.name());
        assertThat(transfer.status()).isEqualTo(COMPLETED.name());
        assertThat(transfer.reference()).isEqualTo("e2e get transfer");
    }

    @Test
    void shouldReturnPreviousTransferForSameIdempotencyKey() throws Exception {
        var tenantId = randomUUID();
        var sourceWallet = createWallet(tenantId, randomUUID(), EUR);
        var destinationWallet = createWallet(tenantId, randomUUID(), EUR);
        deposit(tenantId, sourceWallet.walletId(), ONE_HUNDRED, EUR, "e2e idempotency deposit");

        var request = createTransferRequest(sourceWallet, destinationWallet, TWENTY_FIVE, "e2e idempotent transfer");
        var idempotencyKey = randomUUID().toString();

        var firstTransfer = TRANSFER_API_CLIENT.createTransfer(tenantId, request, idempotencyKey);
        var secondTransfer = TRANSFER_API_CLIENT.createIdempotentTransfer(tenantId, request, idempotencyKey);

        assertThat(secondTransfer.transferId()).isEqualTo(firstTransfer.transferId());
        assertThat(secondTransfer.status()).isEqualTo(COMPLETED.name());
        assertBalance(tenantId, sourceWallet.walletId(), SEVENTY_FIVE);
        assertBalance(tenantId, destinationWallet.walletId(), TWENTY_FIVE);
    }

    @Test
    void shouldRejectSameIdempotencyKeyWithDifferentRequestBody() throws Exception {
        var tenantId = randomUUID();
        var sourceWallet = createWallet(tenantId, randomUUID(), EUR);
        var firstDestinationWallet = createWallet(tenantId, randomUUID(), EUR);
        var secondDestinationWallet = createWallet(tenantId, randomUUID(), EUR);
        deposit(tenantId, sourceWallet.walletId(), ONE_HUNDRED, EUR, "e2e idempotency conflict deposit");

        var idempotencyKey = randomUUID().toString();
        var firstTransfer = TRANSFER_API_CLIENT.createTransfer(
                tenantId,
                createTransferRequest(sourceWallet, firstDestinationWallet, TWENTY_FIVE, "e2e original idempotent transfer"),
                idempotencyKey
        );

        var error = TRANSFER_API_CLIENT.createTransferConflict(
                tenantId,
                createTransferRequest(sourceWallet, secondDestinationWallet, TWENTY_FIVE, "e2e different idempotent transfer"),
                idempotencyKey
        );

        assertThat(error.errorMessage()).contains("Idempotency key").contains("different transfer request");
        assertThat(TRANSFER_API_CLIENT.getTransfer(tenantId, UUID.fromString(firstTransfer.transferId())).destinationWalletId())
                .isEqualTo(firstDestinationWallet.walletId());
        assertBalance(tenantId, sourceWallet.walletId(), SEVENTY_FIVE);
        assertBalance(tenantId, firstDestinationWallet.walletId(), TWENTY_FIVE);
        assertBalance(tenantId, secondDestinationWallet.walletId(), BigDecimal.ZERO);
    }

    @Test
    void shouldNotTransferWalletsAcrossTenants() throws Exception {
        var owningTenantId = randomUUID();
        var otherTenantId = randomUUID();
        var sourceWallet = createWallet(owningTenantId, randomUUID(), EUR);
        var destinationWallet = createWallet(owningTenantId, randomUUID(), EUR);
        deposit(owningTenantId, sourceWallet.walletId(), ONE_HUNDRED, EUR, "e2e multitenancy deposit");

        var error = TRANSFER_API_CLIENT.createTransferNotFound(
                otherTenantId,
                createTransferRequest(sourceWallet, destinationWallet, TWENTY_FIVE, "e2e cross tenant transfer"),
                randomUUID().toString()
        );

        assertThat(error.errorMessage()).contains("was not found");
        assertBalance(owningTenantId, sourceWallet.walletId(), ONE_HUNDRED);
        assertBalance(owningTenantId, destinationWallet.walletId(), BigDecimal.ZERO);
    }

    @Test
    void shouldRejectTransferToSameWallet() throws Exception {
        var tenantId = randomUUID();
        var wallet = createWallet(tenantId, randomUUID(), EUR);
        deposit(tenantId, wallet.walletId(), ONE_HUNDRED, EUR, "e2e same wallet deposit");

        var error = TRANSFER_API_CLIENT.createTransferBadRequest(
                tenantId,
                CreateTransferRequest.builder()
                        .sourceWalletId(UUID.fromString(wallet.walletId()))
                        .destinationWalletId(UUID.fromString(wallet.walletId()))
                        .amount(TWENTY_FIVE)
                        .currency(EUR.name())
                        .reference("e2e same wallet transfer")
                        .build(),
                randomUUID().toString()
        );

        assertThat(error.errorMessage()).isEqualTo("Source and destination wallets must be different");
        assertBalance(tenantId, wallet.walletId(), ONE_HUNDRED);
    }

    @Test
    void shouldRejectZeroTransferAmount() throws Exception {
        var tenantId = randomUUID();
        var sourceWallet = createWallet(tenantId, randomUUID(), EUR);
        var destinationWallet = createWallet(tenantId, randomUUID(), EUR);

        var error = TRANSFER_API_CLIENT.createTransferBadRequest(
                tenantId,
                CreateTransferRequest.builder()
                        .sourceWalletId(UUID.fromString(sourceWallet.walletId()))
                        .destinationWalletId(UUID.fromString(destinationWallet.walletId()))
                        .amount(ZERO)
                        .currency(EUR.name())
                        .reference("e2e zero transfer")
                        .build(),
                randomUUID().toString()
        );

        assertThat(error.errors())
                .anySatisfy(message -> assertThat(message).contains("amount").contains("0.01"));
        assertBalance(tenantId, sourceWallet.walletId(), ZERO);
        assertBalance(tenantId, destinationWallet.walletId(), ZERO);
    }

    private WalletResponse createWallet(UUID tenantId, UUID customerId, CurrencyCode currency) throws Exception {
        var wallet = WALLET_API_CLIENT.createWallet(tenantId, customerId, currency.name());
        assertWallet(tenantId, UUID.fromString(wallet.walletId()));
        return wallet;
    }

    private void deposit(UUID tenantId, String walletId, BigDecimal amount, CurrencyCode currency, String reference) throws Exception {
        WALLET_API_CLIENT.deposit(tenantId, UUID.fromString(walletId), amount, currency.name(), reference);
    }

    private CreateTransferRequest createTransferRequest(
            WalletResponse sourceWallet,
            WalletResponse destinationWallet,
            BigDecimal amount,
            String reference
    ) {
        return CreateTransferRequest.builder()
                .sourceWalletId(UUID.fromString(sourceWallet.walletId()))
                .destinationWalletId(UUID.fromString(destinationWallet.walletId()))
                .amount(amount)
                .currency(EUR.name())
                .reference(reference)
                .build();
    }

    private void assertWallet(UUID tenantId, UUID walletId) throws Exception {
        var response = WALLET_API_CLIENT.getWallet(tenantId, walletId);

        assertThat(response.walletId()).isEqualTo(walletId.toString());
        assertThat(response.tenantId()).isEqualTo(tenantId.toString());
    }

    private void assertBalance(UUID tenantId, String walletId, BigDecimal expectedBalance) throws Exception {
        var response = WALLET_API_CLIENT.getBalance(tenantId, UUID.fromString(walletId));

        assertThat(response.walletId()).isEqualTo(walletId);
        assertThat(response.balance()).isEqualByComparingTo(expectedBalance);
    }

    private void assertTransfer(UUID tenantId, UUID transferId, TransferStatus expectedStatus) throws Exception {
        var response = TRANSFER_API_CLIENT.getTransfer(tenantId, transferId);

        assertThat(response.transferId()).isEqualTo(transferId.toString());
        assertThat(response.tenantId()).isEqualTo(tenantId.toString());
        assertThat(response.status()).isEqualTo(expectedStatus.name());
    }

    private double metricCount(MetricName metricName, String tag) throws Exception {
        var encodedTag = URLEncoder.encode(tag, UTF_8);
        var response = HTTP_CLIENT.get(GET_METRICS_PATH.formatted(metricName.getValue(), encodedTag));

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
}
