package com.nn.safetransfer.audit.infrastructure.persistence;

import com.nn.safetransfer.audit.domain.AuditEvent;
import com.nn.safetransfer.audit.domain.AuditEventRepository;
import com.nn.safetransfer.audit.infrastructure.mapper.AuditEventMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AuditEventRepositoryJpaAdapter implements AuditEventRepository {

    private final SpringDataAuditEventRepository springDataAuditEventRepository;
    private final AuditEventMapper auditEventMapper;

    @Override
    public AuditEvent save(AuditEvent auditEvent) {
        var saved = springDataAuditEventRepository.save(auditEventMapper.toEntity(auditEvent));
        return auditEventMapper.toDomain(saved);
    }
}
