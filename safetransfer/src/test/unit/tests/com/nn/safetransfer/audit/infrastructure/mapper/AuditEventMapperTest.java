package com.nn.safetransfer.audit.infrastructure.mapper;

import com.nn.safetransfer.audit.domain.AuditEvent;
import com.nn.safetransfer.audit.infrastructure.persistence.AuditEventJpa;
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
                .aggregateType("TRANSFER")
                .aggregateId(aggregateId)
                .eventType("TRANSFER_COMPLETED")
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
                () -> assertThat(entity.getAggregateType()).isEqualTo("TRANSFER"),
                () -> assertThat(entity.getAggregateId()).isEqualTo(aggregateId),
                () -> assertThat(entity.getEventType()).isEqualTo("TRANSFER_COMPLETED"),
                () -> assertThat(entity.getPayload()).isEqualTo("{\"transferId\":\"123\"}"),
                () -> assertThat(entity.getRecordedAt()).isEqualTo(recordedAt),
                () -> assertThat(entity.getCorrelationId()).isEqualTo("corr-123"),
                () -> assertThat(entity.getCausationId()).isEqualTo("cause-123")
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
                .aggregateType("TRANSFER")
                .aggregateId(aggregateId)
                .eventType("TRANSFER_COMPLETED")
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
                () -> assertThat(auditEvent.aggregateType()).isEqualTo("TRANSFER"),
                () -> assertThat(auditEvent.aggregateId()).isEqualTo(aggregateId),
                () -> assertThat(auditEvent.eventType()).isEqualTo("TRANSFER_COMPLETED"),
                () -> assertThat(auditEvent.payload()).isEqualTo("{\"transferId\":\"123\"}"),
                () -> assertThat(auditEvent.recordedAt()).isEqualTo(recordedAt),
                () -> assertThat(auditEvent.correlationId()).isEqualTo("corr-123"),
                () -> assertThat(auditEvent.causationId()).isEqualTo("cause-123")
        );
    }
}
