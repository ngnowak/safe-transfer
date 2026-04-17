package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.audit.application.AuditConsumer;
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

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class InProcessOutboxEventDispatcherTest {

    @Mock
    private AuditConsumer auditConsumer;

    @Test
    void shouldDelegateDispatchToAuditConsumer() throws Exception {
        var dispatcher = new InProcessOutboxEventDispatcher(auditConsumer);
        var outboxEvent = buildOutboxEvent();

        dispatcher.dispatch(outboxEvent);

        verify(auditConsumer).consume(outboxEvent);
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
