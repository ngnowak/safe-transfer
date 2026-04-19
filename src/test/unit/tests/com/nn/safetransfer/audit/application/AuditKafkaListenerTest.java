package com.nn.safetransfer.audit.application;

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
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditKafkaListenerTest {

    @Mock
    private AuditConsumer auditConsumer;

    @Mock
    private JsonMapper jsonMapper;

    @InjectMocks
    private AuditKafkaListener listener;

    @Test
    void shouldDeserializeKafkaMessageAndDelegateToAuditConsumer() throws Exception {
        // given
        var outboxEvent = buildOutboxEvent();
        var outboxEventSerialized = "outboxevent";
        given(jsonMapper.readValue(outboxEventSerialized, OutboxEvent.class)).willReturn(outboxEvent);

        // when
        listener.consume(outboxEventSerialized);

        // then
        verify(auditConsumer).consume(outboxEvent);
    }

    @Test
    void shouldWrapDeserializationFailure() {
        // given
        var invalidJson = "not-json";
        given(jsonMapper.readValue(invalidJson, OutboxEvent.class)).willThrow(new RuntimeException("Cannot deserialize"));

        // when/ then
        assertThatThrownBy(() -> listener.consume(invalidJson))
                .isInstanceOf(OutboxProcessingException.class)
                .hasMessage("Failed to deserialize Kafka outbox event");
    }

    private OutboxEvent buildOutboxEvent() {
        return OutboxEvent.builder()
                .id(randomUUID())
                .tenantId(randomUUID())
                .aggregateType(OutboxAggregateType.TRANSFER)
                .aggregateId(randomUUID())
                .eventType(EventType.TRANSFER_COMPLETED)
                .payload("{\"transferId\":\"123\"}")
                .status(OutboxStatus.NEW)
                .occurredAt(Instant.now())
                .retryCount(0)
                .correlationId("corr-123")
                .causationId("cause-123")
                .build();
    }
}
