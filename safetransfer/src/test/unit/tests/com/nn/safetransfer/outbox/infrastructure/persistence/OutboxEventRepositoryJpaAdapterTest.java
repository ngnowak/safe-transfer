package com.nn.safetransfer.outbox.infrastructure.persistence;

import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import com.nn.safetransfer.outbox.infrastructure.mapper.OutboxEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventRepositoryJpaAdapterTest {

    @Mock
    private SpringDataOutboxEventRepository springDataOutboxEventRepository;

    @Mock
    private OutboxEventMapper outboxEventMapper;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private OutboxEventRepositoryJpaAdapter adapter;

    @Test
    void shouldSaveOutboxEventUsingMapper() {
        // given
        var outboxEvent = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .aggregateType(OutboxAggregateType.TRANSFER)
                .aggregateId(UUID.randomUUID())
                .eventType(EventType.TRANSFER_COMPLETED)
                .payload("{\"ok\":true}")
                .status(OutboxStatus.NEW)
                .occurredAt(Instant.parse("2026-04-02T10:15:30Z"))
                .retryCount(0)
                .correlationId("corr")
                .causationId("cause")
                .build();
        var entity = OutboxEventJpa.builder().id(outboxEvent.id()).build();
        var savedEntity = OutboxEventJpa.builder().id(outboxEvent.id()).build();

        given(outboxEventMapper.toEntity(outboxEvent)).willReturn(entity);
        given(springDataOutboxEventRepository.save(entity)).willReturn(savedEntity);
        given(outboxEventMapper.toDomain(savedEntity)).willReturn(outboxEvent);

        // when
        var result = adapter.save(outboxEvent);

        // then
        assertAll(
                () -> assertThat(result).isEqualTo(outboxEvent),
                () -> verify(outboxEventMapper).toEntity(outboxEvent),
                () -> verify(springDataOutboxEventRepository).save(entity),
                () -> verify(outboxEventMapper).toDomain(savedEntity)
        );
    }

    @Test
    void shouldFindPendingOutboxEventsUsingMapper() {
        // given
        var entity = OutboxEventJpa.builder()
                .id(UUID.randomUUID())
                .occurredAt(Instant.parse("2026-04-02T10:15:30Z"))
                .build();
        var outboxEvent = OutboxEvent.builder()
                .id(entity.getId())
                .tenantId(UUID.randomUUID())
                .aggregateType(OutboxAggregateType.TRANSFER)
                .aggregateId(UUID.randomUUID())
                .eventType(EventType.TRANSFER_COMPLETED)
                .payload("{\"ok\":true}")
                .status(OutboxStatus.NEW)
                .occurredAt(entity.getOccurredAt())
                .retryCount(0)
                .build();

        given(entityManager.createNativeQuery(any(String.class), org.mockito.ArgumentMatchers.eq(OutboxEventJpa.class))).willReturn(query);
        given(query.setParameter("maxRetries", 3)).willReturn(query);
        given(query.setParameter("limit", 10)).willReturn(query);
        given(query.getResultList()).willReturn(List.of(entity));
        given(outboxEventMapper.toDomain(entity)).willReturn(outboxEvent);

        // when
        var result = adapter.claimTopRetryableOrderByOccurredAtAsc(10, 3);

        // then
        assertAll(
                () -> assertThat(result).containsExactly(outboxEvent),
                () -> verify(entityManager).createNativeQuery(any(String.class), org.mockito.ArgumentMatchers.eq(OutboxEventJpa.class)),
                () -> verify(query).setParameter("maxRetries", 3),
                () -> verify(query).setParameter("limit", 10),
                () -> verify(outboxEventMapper).toDomain(entity)
        );
    }
}
