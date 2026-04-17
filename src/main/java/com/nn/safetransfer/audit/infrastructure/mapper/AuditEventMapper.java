package com.nn.safetransfer.audit.infrastructure.mapper;

import com.nn.safetransfer.audit.domain.AuditEvent;
import com.nn.safetransfer.audit.infrastructure.persistence.AuditEventJpa;
import org.springframework.stereotype.Component;

@Component
public class AuditEventMapper {

    public AuditEventJpa toEntity(AuditEvent auditEvent) {
        return AuditEventJpa.builder()
                .id(auditEvent.id())
                .sourceEventId(auditEvent.sourceEventId())
                .tenantId(auditEvent.tenantId())
                .aggregateType(auditEvent.aggregateType())
                .aggregateId(auditEvent.aggregateId())
                .eventType(auditEvent.eventType())
                .payload(auditEvent.payload())
                .recordedAt(auditEvent.recordedAt())
                .correlationId(auditEvent.correlationId())
                .causationId(auditEvent.causationId())
                .build();
    }

    public AuditEvent toDomain(AuditEventJpa auditEventJpa) {
        return AuditEvent.builder()
                .id(auditEventJpa.getId())
                .sourceEventId(auditEventJpa.getSourceEventId())
                .tenantId(auditEventJpa.getTenantId())
                .aggregateType(auditEventJpa.getAggregateType())
                .aggregateId(auditEventJpa.getAggregateId())
                .eventType(auditEventJpa.getEventType())
                .payload(auditEventJpa.getPayload())
                .recordedAt(auditEventJpa.getRecordedAt())
                .correlationId(auditEventJpa.getCorrelationId())
                .causationId(auditEventJpa.getCausationId())
                .build();
    }
}
