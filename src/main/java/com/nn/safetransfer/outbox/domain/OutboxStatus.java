package com.nn.safetransfer.outbox.domain;

public enum OutboxStatus {
    NEW,
    PROCESSING,
    PUBLISHED,
    FAILED,
    FATAL
}
