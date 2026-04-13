package com.nn.safetransfer.outbox.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.outbox.application.payload.TransferCompletedPayload;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.transfer.domain.event.TransferCompletedDomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.outbox.domain.OutboxAggregateType.TRANSFER;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.NEW;
import static java.util.UUID.randomUUID;

@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final ObjectMapper objectMapper;

    public OutboxEvent from(TransferCompletedDomainEvent event) {
        var eventId = randomUUID();
        var payload = TransferCompletedPayload.builder()
                .eventId(eventId)
                .occurredAt(event.occurredAt())
                .tenantId(event.tenantId().value())
                .transferId(event.transferId().value())
                .sourceWalletId(event.sourceWalletId().value())
                .destinationWalletId(event.destinationWalletId().value())
                .amount(event.money().amount())
                .currency(event.money().currency().name())
                .reference(event.reference())
                .idempotencyKey(event.idempotencyKey())
                .build();

        return OutboxEvent.builder()
                .id(eventId)
                .tenantId(event.tenantId().value())
                .aggregateType(TRANSFER)
                .aggregateId(event.transferId().value())
                .eventType(TRANSFER_COMPLETED)
                .payload(serialize(payload))
                .status(NEW)
                .occurredAt(event.occurredAt())
                .retryCount(0)
                .correlationId(event.transferId().value().toString())
                .causationId(event.idempotencyKey())
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
