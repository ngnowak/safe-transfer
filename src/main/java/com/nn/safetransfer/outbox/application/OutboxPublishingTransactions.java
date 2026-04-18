package com.nn.safetransfer.outbox.application;

import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Service
@RequiredArgsConstructor
public class OutboxPublishingTransactions {

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(propagation = REQUIRES_NEW)
    public List<OutboxEvent> claimPending(int batchSize, int maxRetries) {
        return outboxEventRepository.claimTopRetryableOrderByOccurredAtAsc(batchSize, maxRetries);
    }

    @Transactional(propagation = REQUIRES_NEW)
    public OutboxEvent save(OutboxEvent outboxEvent) {
        return outboxEventRepository.save(outboxEvent);
    }
}
