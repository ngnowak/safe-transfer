package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.outbox.application.payload.TransferCompletedPayload;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.transfer.domain.event.TransferCompletedDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import static com.nn.safetransfer.outbox.domain.EventType.TRANSFER_COMPLETED;
import static com.nn.safetransfer.outbox.domain.OutboxAggregateType.TRANSFER;
import static com.nn.safetransfer.outbox.domain.OutboxStatus.NEW;
import static java.util.UUID.randomUUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventFactory {

    private final JsonMapper jsonMapper;

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
            return jsonMapper.writeValueAsString(payload);
        } catch (JacksonException ex) {
            var errorMsg = "Failed to serialize outbox payload";
            log.warn(errorMsg, ex);
            throw new IllegalStateException(errorMsg, ex);
        }
    }
}
