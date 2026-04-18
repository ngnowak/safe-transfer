package com.nn.safetransfer.audit.application;

import com.nn.safetransfer.audit.domain.AuditEvent;
import com.nn.safetransfer.audit.domain.AuditEventRepository;
import com.nn.safetransfer.outbox.application.OutboxProcessingException;
import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditConsumerImplTest {

    @Mock
    private AuditEventFactory auditEventFactory;

    @Mock
    private AuditEventRepository auditEventRepository;

    @InjectMocks
    private AuditConsumerImpl consumer;

    @Test
    void shouldSaveAuditEventForSupportedEventType() throws Exception {
        // given
        var outboxEvent = buildOutboxEvent(EventType.TRANSFER_COMPLETED);
        var auditEvent = AuditEvent.builder()
                .id(UUID.randomUUID())
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
        given(auditEventFactory.from(outboxEvent)).willReturn(auditEvent);

        // when
        consumer.consume(outboxEvent);

        // then
        verify(auditEventFactory).from(outboxEvent);
        verify(auditEventRepository).save(auditEvent);
    }

    @Test
    void shouldIgnoreDuplicateAuditEventInsert() throws Exception {
        // given
        var outboxEvent = buildOutboxEvent(EventType.TRANSFER_COMPLETED);
        var auditEvent = AuditEvent.builder()
                .id(UUID.randomUUID())
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
        given(auditEventFactory.from(outboxEvent)).willReturn(auditEvent);
        given(auditEventRepository.save(auditEvent))
                .willThrow(new DataIntegrityViolationException("duplicate"));

        // when
        consumer.consume(outboxEvent);

        // then
        verify(auditEventFactory).from(outboxEvent);
        verify(auditEventRepository).save(auditEvent);
    }

    @Test
    void shouldThrowSpecificProcessingExceptionWhenAuditPersistenceFails() {
        // given
        var outboxEvent = buildOutboxEvent(EventType.TRANSFER_COMPLETED);
        var auditEvent = AuditEvent.builder()
                .id(UUID.randomUUID())
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
        given(auditEventFactory.from(outboxEvent)).willReturn(auditEvent);
        given(auditEventRepository.save(auditEvent))
                .willThrow(new DataAccessResourceFailureException("db down"));

        // when / then
        assertThatThrownBy(() -> consumer.consume(outboxEvent))
                .isInstanceOf(OutboxProcessingException.class)
                .hasMessageContaining(outboxEvent.id().toString());
    }

    private OutboxEvent buildOutboxEvent(EventType eventType) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .aggregateType(OutboxAggregateType.TRANSFER)
                .aggregateId(UUID.randomUUID())
                .eventType(eventType)
                .payload("{\"transferId\":\"123\"}")
                .status(OutboxStatus.NEW)
                .occurredAt(Instant.parse("2026-04-02T10:15:30Z"))
                .retryCount(0)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();
    }
}
