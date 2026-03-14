package com.waddoc.domain.audit.service;

import com.waddoc.domain.audit.entity.AuditLog;
import com.waddoc.domain.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void log(String action, String targetType, String targetId,
                    String correlationId, Map<String, Object> detailJson) {
        log(action, targetType, targetId, correlationId, "SYSTEM", "SYSTEM", detailJson);
    }

    @Transactional
    public void log(String action, String targetType, String targetId,
                    String correlationId, String actorId, String actorRole,
                    Map<String, Object> detailJson) {
        AuditLog auditLog = AuditLog.builder()
                .actorId(actorId)
                .actorRole(actorRole)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .correlationId(correlationId)
                .detailJson(detailJson)
                .build();
        auditLogRepository.save(auditLog);
    }
}
