package com.nn.safetransfer.audit.application;

import com.nn.safetransfer.audit.domain.AuditEvent;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;

import static java.util.UUID.randomUUID;

@Component
public class AuditEventFactory {

    public AuditEvent from(OutboxEvent outboxEvent) {
        return AuditEvent.builder()
                .id(randomUUID())
                .sourceEventId(outboxEvent.id())
                .tenantId(outboxEvent.tenantId())
                .aggregateType(outboxEvent.aggregateType().name())
                .aggregateId(outboxEvent.aggregateId())
                .eventType(outboxEvent.eventType().name())
                .payload(outboxEvent.payload())
                .recordedAt(Instant.now())
                .correlationId(outboxEvent.correlationId())
                .causationId(outboxEvent.causationId())
                .build();
    }
}
