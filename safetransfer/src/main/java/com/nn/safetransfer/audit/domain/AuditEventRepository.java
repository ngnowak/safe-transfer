package com.nn.safetransfer.audit.domain;

public interface AuditEventRepository {
    AuditEvent save(AuditEvent auditEvent);
}
