package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.outbox.domain.OutboxEvent;

public interface OutboxEventDispatcher {
    void dispatch(OutboxEvent outboxEvent) throws OutboxProcessingException;
}
