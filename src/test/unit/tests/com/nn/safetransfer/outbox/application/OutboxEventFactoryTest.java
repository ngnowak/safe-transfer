package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.outbox.application.payload.TransferCompletedPayload;
import com.nn.safetransfer.transfer.domain.Transfer;
import com.nn.safetransfer.transfer.domain.event.TransferCompletedDomainEvent;
import com.nn.safetransfer.wallet.domain.TenantId;
import com.nn.safetransfer.wallet.domain.WalletId;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static com.nn.safetransfer.TestAmounts.FIFTY;
import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.outbox.domain.OutboxAggregateType.TRANSFER;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.NEW;
import static com.nn.safetransfer.wallet.domain.CurrencyCode.EUR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OutboxEventFactoryTest {

    private final JsonMapper jsonMapper = new JsonMapper();

    private final OutboxEventFactory factory = new OutboxEventFactory(jsonMapper);

    @Test
    void shouldCreateTransferCompletedOutboxEvent() {
        // given
        var tenantId = TenantId.create();
        var sourceWalletId = WalletId.create();
        var destinationWalletId = WalletId.create();
        var transfer = Transfer.completed(
                tenantId,
                sourceWalletId,
                destinationWalletId,
                FIFTY,
                EUR,
                "idem-123",
                "test-hash",
                "Payment"
        );

        // when
        var domainEvent = TransferCompletedDomainEvent.from(transfer);
        var event = factory.from(domainEvent);
        var payload = jsonMapper.readValue(event.payload(), TransferCompletedPayload.class);

        // then
        assertAll(
                () -> assertThat(event.id()).isNotNull(),
                () -> assertThat(event.tenantId()).isEqualTo(tenantId.value()),
                () -> assertThat(event.aggregateType()).isEqualTo(TRANSFER),
                () -> assertThat(event.aggregateId()).isEqualTo(transfer.getId().value()),
                () -> assertThat(event.eventType()).isEqualTo(TRANSFER_COMPLETED),
                () -> assertThat(event.status()).isEqualTo(NEW),
                () -> assertThat(event.occurredAt()).isEqualTo(domainEvent.occurredAt()),
                () -> assertThat(event.retryCount()).isZero(),
                () -> assertThat(event.correlationId()).isEqualTo(transfer.getId().value().toString()),
                () -> assertThat(event.causationId()).isEqualTo(transfer.getIdempotencyKey()),
                () -> assertThat(payload.eventId()).isEqualTo(event.id()),
                () -> assertThat(payload.occurredAt()).isEqualTo(event.occurredAt()),
                () -> assertThat(payload.tenantId()).isEqualTo(tenantId.value()),
                () -> assertThat(payload.transferId()).isEqualTo(transfer.getId().value()),
                () -> assertThat(payload.sourceWalletId()).isEqualTo(sourceWalletId.value()),
                () -> assertThat(payload.destinationWalletId()).isEqualTo(destinationWalletId.value()),
                () -> assertThat(payload.amount()).isEqualByComparingTo(transfer.getAmount()),
                () -> assertThat(payload.currency()).isEqualTo(EUR.name()),
                () -> assertThat(payload.reference()).isEqualTo(transfer.getReference()),
                () -> assertThat(payload.idempotencyKey()).isEqualTo(transfer.getIdempotencyKey())
        );
    }
}
