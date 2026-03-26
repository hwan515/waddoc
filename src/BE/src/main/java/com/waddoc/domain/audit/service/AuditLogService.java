package com.waddoc.domain.audit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 도메인 이벤트를 공통 형식의 감사 로그로 남긴다.
 */
@Slf4j
@Service
public class AuditLogService {

    public void log(String action, String targetType, String targetId,
                    String correlationId, Map<String, Object> detailJson) {
        log(action, targetType, targetId, correlationId, "SYSTEM", "SYSTEM", detailJson);
    }

    public void log(String action, String targetType, String targetId,
                    String correlationId, String actorId, String actorRole,
                    Map<String, Object> detailJson) {
        log.info(
                "audit action={}, targetType={}, targetId={}, correlationId={}, actorId={}, actorRole={}, detail={}",
                action,
                targetType,
                targetId,
                correlationId,
                actorId,
                actorRole,
                detailJson
        );
    }
}
