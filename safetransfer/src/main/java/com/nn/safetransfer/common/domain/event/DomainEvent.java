package com.nn.safetransfer.common.domain.event;

import java.time.Instant;

public interface DomainEvent {
    Instant occurredAt();
}
