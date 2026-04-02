package com.nn.safetransfer.audit.domain;

import lombok.Builder;
import lombok.With;

import java.time.Instant;
import java.util.UUID;

@With
@Builder
public record AuditEvent(
        UUID id,
        UUID sourceEventId,
        UUID tenantId,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String payload,
        Instant recordedAt,
        String correlationId,
        String causationId
) {
}
