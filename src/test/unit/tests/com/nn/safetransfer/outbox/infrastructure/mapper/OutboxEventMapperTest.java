package com.nn.safetransfer.outbox.infrastructure.mapper;

import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import com.nn.safetransfer.outbox.infrastructure.persistence.OutboxEventJpa;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class OutboxEventMapperTest {

    private final OutboxEventMapper mapper = new OutboxEventMapper();

    @Test
    void shouldMapOutboxEventToEntity() {
        // given
        var id = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var aggregateId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-04-02T10:15:30Z");
        var claimedAt = Instant.parse("2026-04-02T10:16:00Z");
        var publishedAt = Instant.parse("2026-04-02T10:16:30Z");
        var outboxEvent = OutboxEvent.builder()
                .id(id)
                .tenantId(tenantId)
                .aggregateType(OutboxAggregateType.TRANSFER)
                .aggregateId(aggregateId)
                .eventType(EventType.TRANSFER_COMPLETED)
                .payload("{\"transferId\":\"123\"}")
                .status(OutboxStatus.NEW)
                .occurredAt(occurredAt)
                .claimedAt(claimedAt)
                .publishedAt(publishedAt)
                .retryCount(2)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();

        // when
        var entity = mapper.toEntity(outboxEvent);

        // then
        assertAll(
                () -> assertThat(entity.getId()).isEqualTo(id),
                () -> assertThat(entity.getTenantId()).isEqualTo(tenantId),
                () -> assertThat(entity.getAggregateType()).isEqualTo(outboxEvent.aggregateType().name()),
                () -> assertThat(entity.getAggregateId()).isEqualTo(aggregateId),
                () -> assertThat(entity.getEventType()).isEqualTo(outboxEvent.eventType().name()),
                () -> assertThat(entity.getPayload()).isEqualTo(outboxEvent.payload()),
                () -> assertThat(entity.getStatus()).isEqualTo(outboxEvent.status().name()),
                () -> assertThat(entity.getOccurredAt()).isEqualTo(occurredAt),
                () -> assertThat(entity.getClaimedAt()).isEqualTo(claimedAt),
                () -> assertThat(entity.getPublishedAt()).isEqualTo(publishedAt),
                () -> assertThat(entity.getRetryCount()).isEqualTo(2),
                () -> assertThat(entity.getCorrelationId()).isEqualTo("corr-123"),
                () -> assertThat(entity.getCausationId()).isEqualTo("cause-123")
        );
    }

    @Test
    void shouldMapEntityToOutboxEvent() {
        // given
        var id = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        var aggregateId = UUID.randomUUID();
        var occurredAt = Instant.parse("2026-04-02T10:15:30Z");
        var claimedAt = Instant.parse("2026-04-02T10:16:00Z");
        var publishedAt = Instant.parse("2026-04-02T10:16:30Z");
        var entity = OutboxEventJpa.builder()
                .id(id)
                .tenantId(tenantId)
                .aggregateType(OutboxAggregateType.TRANSFER.name())
                .aggregateId(aggregateId)
                .eventType(EventType.TRANSFER_COMPLETED.name())
                .payload("{\"transferId\":\"123\"}")
                .status(OutboxStatus.PUBLISHED.name())
                .occurredAt(occurredAt)
                .claimedAt(claimedAt)
                .publishedAt(publishedAt)
                .retryCount(1)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();

        // when
        var outboxEvent = mapper.toDomain(entity);

        // then
        assertAll(
                () -> assertThat(outboxEvent.id()).isEqualTo(id),
                () -> assertThat(outboxEvent.tenantId()).isEqualTo(tenantId),
                () -> assertThat(outboxEvent.aggregateType()).isEqualTo(OutboxAggregateType.TRANSFER),
                () -> assertThat(outboxEvent.aggregateId()).isEqualTo(aggregateId),
                () -> assertThat(outboxEvent.eventType()).isEqualTo(EventType.TRANSFER_COMPLETED),
                () -> assertThat(outboxEvent.payload()).isEqualTo("{\"transferId\":\"123\"}"),
                () -> assertThat(outboxEvent.status()).isEqualTo(OutboxStatus.PUBLISHED),
                () -> assertThat(outboxEvent.occurredAt()).isEqualTo(occurredAt),
                () -> assertThat(outboxEvent.claimedAt()).isEqualTo(claimedAt),
                () -> assertThat(outboxEvent.publishedAt()).isEqualTo(publishedAt),
                () -> assertThat(outboxEvent.retryCount()).isEqualTo(1),
                () -> assertThat(outboxEvent.correlationId()).isEqualTo("corr-123"),
                () -> assertThat(outboxEvent.causationId()).isEqualTo("cause-123")
        );
    }
}
