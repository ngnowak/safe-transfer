package com.nn.safetransfer.outbox.domain;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);
}
