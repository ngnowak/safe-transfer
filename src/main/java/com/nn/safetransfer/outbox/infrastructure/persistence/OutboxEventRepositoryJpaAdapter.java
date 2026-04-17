package com.nn.safetransfer.outbox.infrastructure.persistence;

import com.nn.safetransfer.outbox.domain.OutboxEvent;
import com.nn.safetransfer.outbox.domain.OutboxEventRepository;
import com.nn.safetransfer.outbox.infrastructure.mapper.OutboxEventMapper;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryJpaAdapter implements OutboxEventRepository {

    private final SpringDataOutboxEventRepository springDataOutboxEventRepository;
    private final OutboxEventMapper outboxEventMapper;
    private final EntityManager entityManager;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        var saved = springDataOutboxEventRepository.save(outboxEventMapper.toEntity(outboxEvent));
        return outboxEventMapper.toDomain(saved);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<OutboxEvent> claimTopRetryableOrderByOccurredAtAsc(int limit, int maxRetries) {
        var query = entityManager.createNativeQuery("""
                with claimed as (
                    select id
                    from outbox_event
                    where retry_count < :maxRetries
                      and (
                          status in ('NEW', 'FAILED')
                          or (
                              status = 'PROCESSING'
                              and claimed_at < now() - interval '5 minutes'
                          )
                      )
                    order by occurred_at asc
                    for update skip locked
                    limit :limit
                )
                update outbox_event event
                set status = 'PROCESSING',
                    claimed_at = now()
                from claimed
                where event.id = claimed.id
                returning event.*
                """, OutboxEventJpa.class);
        query.setParameter("maxRetries", maxRetries);
        query.setParameter("limit", limit);

        return ((List<OutboxEventJpa>) query.getResultList())
                .stream()
                .map(outboxEventMapper::toDomain)
                .toList();
    }
}
