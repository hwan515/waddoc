package com.waddoc.domain.audit.entity;

import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @Column(name = "log_timestamp", nullable = false)
    private LocalDateTime logTimestamp;

    @Column(name = "actor_id", nullable = false, length = 20)
    private String actorId;

    @Column(name = "actor_role", nullable = false, length = 20)
    private String actorRole;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", length = 20)
    private String targetId;

    @Column(name = "correlation_id", nullable = false, length = 40)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail_json", columnDefinition = "jsonb")
    private Map<String, Object> detailJson;

    @Builder
    public AuditLog(String actorId, String actorRole, String action,
                    String targetType, String targetId, String correlationId,
                    Map<String, Object> detailJson) {
        this.publicId = PublicIdGenerator.generate("log_");
        this.logTimestamp = LocalDateTime.now();
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.correlationId = correlationId;
        this.detailJson = detailJson;
    }
}
