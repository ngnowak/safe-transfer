package com.nn.safetransfer.audit.application;

import com.nn.safetransfer.outbox.application.OutboxProcessingException;
import com.nn.safetransfer.outbox.domain.OutboxEvent;

public interface AuditConsumer {
    void consume(OutboxEvent outboxEvent) throws OutboxProcessingException;
}
