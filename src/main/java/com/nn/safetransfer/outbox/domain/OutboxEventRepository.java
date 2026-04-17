package com.nn.safetransfer.outbox.domain;

import java.util.List;

public interface OutboxEventRepository {

    OutboxEvent save(OutboxEvent outboxEvent);

    List<OutboxEvent> claimTopRetryableOrderByOccurredAtAsc(int limit, int maxRetries);
}
