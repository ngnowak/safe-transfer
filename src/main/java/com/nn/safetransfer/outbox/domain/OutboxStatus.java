package com.nn.safetransfer.outbox.domain;

public enum OutboxStatus {
    NEW,
    PUBLISHED,
    FAILED,
    FATAL
}
