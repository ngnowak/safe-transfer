package com.nn.safetransfer.outbox.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.outbox.application.payload.TransferCompletedPayload;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OutboxEventFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final OutboxEventFactory factory = new OutboxEventFactory(objectMapper);

    @Test
    void shouldCreateTransferCompletedOutboxEvent() throws Exception {
        // given
        var tenantId = TenantId.create();
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var transfer = Transfer.completed(
                tenantId,
                sourceWalletId,
                destinationWalletId,
                new BigDecimal("50.00"),
                EUR,
                "idem-123",
                "Payment"
        );

        // when
        var event = factory.transferCompleted(transfer);
        var payload = objectMapper.readValue(event.payload(), TransferCompletedPayload.class);

        // then
        assertAll(
                () -> assertThat(event.id()).isNotNull(),
                () -> assertThat(event.tenantId()).isEqualTo(tenantId.value()),
                () -> assertThat(event.aggregateType()).isEqualTo(OutboxAggregateType.TRANSFER),
                () -> assertThat(event.aggregateId()).isEqualTo(transfer.getId().value()),
                () -> assertThat(event.eventType()).isEqualTo(TRANSFER_COMPLETED),
                () -> assertThat(event.status()).isEqualTo(OutboxStatus.NEW),
                () -> assertThat(event.occurredAt()).isNotNull(),
                () -> assertThat(event.retryCount()).isZero(),
                () -> assertThat(event.correlationId()).isEqualTo(transfer.getId().value().toString()),
                () -> assertThat(event.causationId()).isEqualTo("idem-123"),
                () -> assertThat(payload.eventId()).isEqualTo(event.id()),
                () -> assertThat(payload.occurredAt()).isEqualTo(event.occurredAt()),
                () -> assertThat(payload.tenantId()).isEqualTo(tenantId.value()),
                () -> assertThat(payload.transferId()).isEqualTo(transfer.getId().value()),
                () -> assertThat(payload.sourceWalletId()).isEqualTo(sourceWalletId.value()),
                () -> assertThat(payload.destinationWalletId()).isEqualTo(destinationWalletId.value()),
                () -> assertThat(payload.amount()).isEqualByComparingTo("50.00"),
                () -> assertThat(payload.currency()).isEqualTo("EUR"),
                () -> assertThat(payload.reference()).isEqualTo("Payment"),
                () -> assertThat(payload.idempotencyKey()).isEqualTo("idem-123")
        );
    }
}
