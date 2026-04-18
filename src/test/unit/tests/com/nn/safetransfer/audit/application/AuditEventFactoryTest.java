package com.nn.safetransfer.audit.application;

import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class AuditEventFactoryTest {

    private final AuditEventFactory factory = new AuditEventFactory();

    @Test
    void shouldMapOutboxEventToAuditEvent() {
        // given
        var eventId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var aggregateId = UUID.randomUUID();
        var outboxEvent = OutboxEvent.builder()
                .id(eventId)
                .tenantId(tenantId)
                .aggregateType(OutboxAggregateType.TRANSFER)
                .aggregateId(aggregateId)
                .eventType(EventType.TRANSFER_COMPLETED)
                .payload("{\"transferId\":\"123\"}")
                .status(OutboxStatus.NEW)
                .occurredAt(Instant.parse("2026-04-02T10:15:30Z"))
                .retryCount(0)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();

        // when
        var auditEvent = factory.from(outboxEvent);

        // then
        assertAll(
                () -> assertThat(auditEvent.id()).isNotNull(),
                () -> assertThat(auditEvent.id()).isNotEqualTo(eventId),
                () -> assertThat(auditEvent.sourceEventId()).isEqualTo(eventId),
                () -> assertThat(auditEvent.tenantId()).isEqualTo(tenantId),
                () -> assertThat(auditEvent.aggregateType()).isEqualTo(outboxEvent.aggregateType().name()),
                () -> assertThat(auditEvent.aggregateId()).isEqualTo(aggregateId),
                () -> assertThat(auditEvent.eventType()).isEqualTo(outboxEvent.eventType().name()),
                () -> assertThat(auditEvent.payload()).isEqualTo(outboxEvent.payload()),
                () -> assertThat(auditEvent.recordedAt()).isNotNull(),
                () -> assertThat(auditEvent.correlationId()).isEqualTo(outboxEvent.correlationId()),
                () -> assertThat(auditEvent.causationId()).isEqualTo(outboxEvent.causationId())
        );
    }
}
