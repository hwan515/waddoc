package com.waddoc.domain.intake.entity;

import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.audit.BaseCreatedEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 전화 시뮬레이터 인테이크 세션. patient_id는 nullable (식별 전 생성 허용).
 * publicId가 capability token 역할 수행.
 */
@Entity
@Table(name = "intake_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IntakeSession extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "intake_session_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "caller_number", length = 20)
    private String callerNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IntakeChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IntakeStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "completion_reason", length = 50)
    private CompletionReason completionReason;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Builder
    public IntakeSession(Patient patient, String callerNumber, IntakeChannel channel) {
        this.publicId = PublicIdGenerator.generate("ints_");
        this.patient = patient;
        this.callerNumber = callerNumber;
        this.channel = channel != null ? channel : IntakeChannel.WEB_SIMULATOR;
        this.status = IntakeStatus.STARTED;
        this.lastActivityAt = LocalDateTime.now();
    }

    /** 환자 식별 완료 후 세션에 바인딩 */
    public void bindPatient(Patient patient) {
        this.patient = patient;
        if (this.status == IntakeStatus.STARTED) {
            this.status = IntakeStatus.IN_PROGRESS;
        }
        touch();
    }

    /** 세션 종료 처리 */
    public void complete(CompletionReason completionReason) {
        this.status = IntakeStatus.COMPLETED;
        this.completionReason = completionReason;
        this.endedAt = LocalDateTime.now();
        touch();
    }

    public void markInProgress() {
        if (this.status == IntakeStatus.STARTED) {
            this.status = IntakeStatus.IN_PROGRESS;
        }
    }

    /** 세션이 활성 상태(STARTED 또는 IN_PROGRESS)인지 확인 */
    public boolean isActive() {
        return this.status == IntakeStatus.STARTED || this.status == IntakeStatus.IN_PROGRESS;
    }

    /** 마지막 활동 시각 갱신 */
    public void touch() {
        this.lastActivityAt = LocalDateTime.now();
    }
}
