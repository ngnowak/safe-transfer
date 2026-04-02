package com.nn.safetransfer.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.outbox.application.payload.TransferCompletedPayload;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.transfer.domain.Transfer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.outbox.domain.OutboxAggregateType.TRANSFER;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.NEW;
import static java.util.UUID.randomUUID;

@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEvent transferCompleted(Transfer transfer) {
        var eventId = randomUUID();
        var occurredAt = Instant.now();
        var payload = TransferCompletedPayload.builder()
                .eventId(eventId)
                .occurredAt(occurredAt)
                .tenantId(transfer.getTenantId().value())
                .transferId(transfer.getId().value())
                .sourceWalletId(transfer.getSourceWalletId().value())
                .destinationWalletId(transfer.getDestinationWalletId().value())
                .amount(transfer.getAmount())
                .currency(transfer.getCurrency().name())
                .reference(transfer.getReference())
                .idempotencyKey(transfer.getIdempotencyKey())
                .build();

        return OutboxEvent.builder()
                .id(eventId)
                .tenantId(transfer.getTenantId().value())
                .aggregateType(TRANSFER)
                .aggregateId(transfer.getId().value())
                .eventType(TRANSFER_COMPLETED)
                .payload(serialize(payload))
                .status(NEW)
                .occurredAt(occurredAt)
                .retryCount(0)
                .correlationId(transfer.getId().value().toString())
                .causationId(transfer.getIdempotencyKey())
                .build();
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize outbox payload", ex);
        }
    }
}
