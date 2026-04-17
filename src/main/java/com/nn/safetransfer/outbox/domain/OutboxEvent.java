package com.nn.safetransfer.outbox.domain;

import lombok.Builder;
import lombok.With;

import java.time.Instant;
import java.util.UUID;

@With
@Builder
public record OutboxEvent(
        UUID id,
        UUID tenantId,
        OutboxAggregateType aggregateType,
        UUID aggregateId,
        EventType eventType,
        String payload,
        OutboxStatus status,
        Instant occurredAt,
        Instant claimedAt,
        Instant publishedAt,
        int retryCount,
        String correlationId,
        String causationId
) {
}
