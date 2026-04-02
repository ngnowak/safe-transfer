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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventRepositoryJpaAdapterTest {

    @Mock
    private SpringDataOutboxEventRepository springDataOutboxEventRepository;

    @Mock
    private OutboxEventMapper outboxEventMapper;

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
}
