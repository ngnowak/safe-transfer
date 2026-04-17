package com.nn.safetransfer.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nn.safetransfer.outbox.application.OutboxProcessingException;
import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxAggregateType;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditKafkaListenerTest {

    @Mock
    private AuditConsumer auditConsumer;

    @Test
    void shouldDeserializeKafkaMessageAndDelegateToAuditConsumer() throws Exception {
        var objectMapper = new ObjectMapper().findAndRegisterModules();
        var listener = new AuditKafkaListener(objectMapper, auditConsumer);
        var outboxEvent = buildOutboxEvent();

        listener.consume(objectMapper.writeValueAsString(outboxEvent));

        verify(auditConsumer).consume(outboxEvent);
    }

    @Test
    void shouldWrapDeserializationFailure() {
        var listener = new AuditKafkaListener(new ObjectMapper().findAndRegisterModules(), auditConsumer);

        assertThatThrownBy(() -> listener.consume("not-json"))
                .isInstanceOf(OutboxProcessingException.class)
                .hasMessage("Failed to deserialize Kafka outbox event");
    }

    private OutboxEvent buildOutboxEvent() {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .aggregateType(OutboxAggregateType.TRANSFER)
                .aggregateId(UUID.randomUUID())
                .eventType(EventType.TRANSFER_COMPLETED)
                .payload("{\"transferId\":\"123\"}")
                .status(OutboxStatus.NEW)
                .occurredAt(Instant.parse("2026-04-02T10:15:30Z"))
                .retryCount(0)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();
    }
}
