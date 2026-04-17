package com.nn.safetransfer.outbox.infrastructure.mapper;

import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import com.nn.safetransfer.outbox.infrastructure.persistence.OutboxEventJpa;
import org.springframework.stereotype.Component;

@Component
public class OutboxEventMapper {

    public OutboxEventJpa toEntity(OutboxEvent outboxEvent) {
        return OutboxEventJpa.builder()
                .id(outboxEvent.id())
                .tenantId(outboxEvent.tenantId())
                .aggregateType(outboxEvent.aggregateType().name())
                .aggregateId(outboxEvent.aggregateId())
                .eventType(outboxEvent.eventType().name())
                .payload(outboxEvent.payload())
                .status(outboxEvent.status().name())
                .occurredAt(outboxEvent.occurredAt())
                .claimedAt(outboxEvent.claimedAt())
                .publishedAt(outboxEvent.publishedAt())
                .retryCount(outboxEvent.retryCount())
                .correlationId(outboxEvent.correlationId())
                .causationId(outboxEvent.causationId())
                .build();
    }

    public OutboxEvent toDomain(OutboxEventJpa outboxEventJpa) {
        return OutboxEvent.builder()
                .id(outboxEventJpa.getId())
                .tenantId(outboxEventJpa.getTenantId())
                .aggregateType(OutboxAggregateType.valueOf(outboxEventJpa.getAggregateType()))
                .aggregateId(outboxEventJpa.getAggregateId())
                .eventType(EventType.valueOf(outboxEventJpa.getEventType()))
                .payload(outboxEventJpa.getPayload())
                .status(OutboxStatus.valueOf(outboxEventJpa.getStatus()))
                .occurredAt(outboxEventJpa.getOccurredAt())
                .claimedAt(outboxEventJpa.getClaimedAt())
                .publishedAt(outboxEventJpa.getPublishedAt())
                .retryCount(outboxEventJpa.getRetryCount())
                .correlationId(outboxEventJpa.getCorrelationId())
                .causationId(outboxEventJpa.getCausationId())
                .build();
    }
}
