package com.nn.safetransfer.outbox.application;

public interface OutboxPublisher {

    int publishPending(int batchSize);
}
