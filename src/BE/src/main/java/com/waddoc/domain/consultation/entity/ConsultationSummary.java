package com.waddoc.domain.consultation.entity;

import com.waddoc.global.audit.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "consultation_summary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultationSummary extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "summary_id")
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false, unique = true)
    private ConsultationSession session;

    @Column(name = "summary_note", nullable = false, columnDefinition = "TEXT")
    private String summaryNote;

    @Column(name = "is_prescription_issued", nullable = false)
    private boolean prescriptionIssued;

    @Column(name = "prescription_note", columnDefinition = "TEXT")
    private String prescriptionNote;

    @Column(name = "needs_follow_up", nullable = false)
    private boolean needsFollowUp;

    @Builder
    public ConsultationSummary(ConsultationSession session, String summaryNote,
                               boolean prescriptionIssued, String prescriptionNote,
                               boolean needsFollowUp) {
        this.session = session;
        this.summaryNote = summaryNote;
        this.prescriptionIssued = prescriptionIssued;
        this.prescriptionNote = prescriptionNote;
        this.needsFollowUp = needsFollowUp;
    }
}
