package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.audit.application.AuditConsumer;
import com.nn.safetransfer.outbox.domain.OutboxEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "application.kafka", name = "publishing", havingValue = "false", matchIfMissing = true)
public class InProcessOutboxEventDispatcher implements OutboxEventDispatcher {

    private final AuditConsumer auditConsumer;

    @Override
    public void dispatch(OutboxEvent outboxEvent) throws OutboxProcessingException {
        auditConsumer.consume(outboxEvent);
    }
}
