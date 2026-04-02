package com.nn.safetransfer.audit.infrastructure.persistence;

import com.nn.safetransfer.audit.domain.AuditEvent;
import com.nn.safetransfer.audit.infrastructure.mapper.AuditEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuditEventRepositoryJpaAdapterTest {

    @Mock
    private SpringDataAuditEventRepository springDataAuditEventRepository;

    @Mock
    private AuditEventMapper auditEventMapper;

    @InjectMocks
    private AuditEventRepositoryJpaAdapter adapter;

    @Test
    void shouldSaveAuditEventUsingMapper() {
        // given
        var auditEvent = AuditEvent.builder()
                .id(UUID.randomUUID())
                .sourceEventId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .aggregateType("TRANSFER")
                .aggregateId(UUID.randomUUID())
                .eventType("TRANSFER_COMPLETED")
                .payload("{\"ok\":true}")
                .recordedAt(Instant.parse("2026-04-02T10:15:30Z"))
                .correlationId("corr")
                .causationId("cause")
                .build();
        var entity = AuditEventJpa.builder().id(auditEvent.id()).build();
        var savedEntity = AuditEventJpa.builder().id(auditEvent.id()).build();

        given(auditEventMapper.toEntity(auditEvent)).willReturn(entity);
        given(springDataAuditEventRepository.save(entity)).willReturn(savedEntity);
        given(auditEventMapper.toDomain(savedEntity)).willReturn(auditEvent);

        // when
        var result = adapter.save(auditEvent);

        // then
        assertAll(
                () -> assertThat(result).isEqualTo(auditEvent),
                () -> verify(auditEventMapper).toEntity(auditEvent),
                () -> verify(springDataAuditEventRepository).save(entity),
                () -> verify(auditEventMapper).toDomain(savedEntity)
        );
    }
}
