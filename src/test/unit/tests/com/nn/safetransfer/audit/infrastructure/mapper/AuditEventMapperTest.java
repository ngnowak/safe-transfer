package com.nn.safetransfer.audit.infrastructure.mapper;

import com.nn.safetransfer.audit.domain.AuditEvent;
import com.nn.safetransfer.audit.infrastructure.persistence.AuditEventJpa;
import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class AuditEventMapperTest {

    private final AuditEventMapper mapper = new AuditEventMapper();

    @Test
    void shouldMapAuditEventToEntity() {
        // given
        var id = UUID.randomUUID();
        var sourceEventId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var aggregateId = UUID.randomUUID();
        var recordedAt = Instant.parse("2026-04-02T10:15:30Z");
        var auditEvent = AuditEvent.builder()
                .id(id)
                .sourceEventId(sourceEventId)
                .tenantId(tenantId)
                .aggregateType(OutboxAggregateType.TRANSFER.name())
                .aggregateId(aggregateId)
                .eventType(EventType.TRANSFER_COMPLETED.name())
                .payload("{\"transferId\":\"123\"}")
                .recordedAt(recordedAt)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();

        // when
        var entity = mapper.toEntity(auditEvent);

        // then
        assertAll(
                () -> assertThat(entity.getId()).isEqualTo(id),
                () -> assertThat(entity.getSourceEventId()).isEqualTo(sourceEventId),
                () -> assertThat(entity.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(entity.getAggregateType()).isEqualTo(auditEvent.aggregateType()),
                () -> assertThat(entity.getAggregateId()).isEqualTo(aggregateId),
                () -> assertThat(entity.getEventType()).isEqualTo(auditEvent.eventType()),
                () -> assertThat(entity.getPayload()).isEqualTo(auditEvent.payload()),
                () -> assertThat(entity.getRecordedAt()).isEqualTo(recordedAt),
                () -> assertThat(entity.getCorrelationId()).isEqualTo(auditEvent.correlationId()),
                () -> assertThat(entity.getCausationId()).isEqualTo(auditEvent.causationId())
        );
    }

    @Test
    void shouldMapEntityToAuditEvent() {
        // given
        var id = UUID.randomUUID();
        var sourceEventId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var aggregateId = UUID.randomUUID();
        var recordedAt = Instant.parse("2026-04-02T10:15:30Z");
        var entity = AuditEventJpa.builder()
                .id(id)
                .sourceEventId(sourceEventId)
                .tenantId(tenantId)
                .aggregateType(OutboxAggregateType.TRANSFER.name())
                .aggregateId(aggregateId)
                .eventType(EventType.TRANSFER_COMPLETED.name())
                .payload("{\"transferId\":\"123\"}")
                .recordedAt(recordedAt)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();

        // when
        var auditEvent = mapper.toDomain(entity);

        // then
        assertAll(
                () -> assertThat(auditEvent.id()).isEqualTo(id),
                () -> assertThat(auditEvent.sourceEventId()).isEqualTo(sourceEventId),
                () -> assertThat(auditEvent.tenantId()).isEqualTo(tenantId),
                () -> assertThat(auditEvent.aggregateType()).isEqualTo(entity.getAggregateType()),
                () -> assertThat(auditEvent.aggregateId()).isEqualTo(aggregateId),
                () -> assertThat(auditEvent.eventType()).isEqualTo(entity.getEventType()),
                () -> assertThat(auditEvent.payload()).isEqualTo(entity.getPayload()),
                () -> assertThat(auditEvent.recordedAt()).isEqualTo(recordedAt),
                () -> assertThat(auditEvent.correlationId()).isEqualTo(entity.getCorrelationId()),
                () -> assertThat(auditEvent.causationId()).isEqualTo(entity.getCausationId())
        );
    }
}
