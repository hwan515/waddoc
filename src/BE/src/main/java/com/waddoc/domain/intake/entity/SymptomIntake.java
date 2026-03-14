package com.waddoc.domain.intake.entity;

import com.waddoc.global.audit.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수집된 증상 원문 및 분류 결과. 세션당 1건.
 */
@Entity
@Table(name = "symptom_intake")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SymptomIntake extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "symptom_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_session_id", nullable = false)
    private IntakeSession intakeSession;

    @Column(name = "symptom_text", nullable = false, columnDefinition = "TEXT")
    private String symptomText;

    @Column(name = "symptom_category", length = 50)
    private String symptomCategory;

    @Column(name = "is_emergency", nullable = false)
    private boolean emergency;

    @Builder
    public SymptomIntake(IntakeSession intakeSession, String symptomText, String symptomCategory, boolean emergency) {
        this.intakeSession = intakeSession;
        this.symptomText = symptomText;
        this.symptomCategory = symptomCategory;
        this.emergency = emergency;
    }
}
