package com.waddoc.domain.intake.entity;

import com.waddoc.global.audit.BaseCreatedEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진료과 추천 결과. 증상 분석 후 진료과/신뢰도/응급 여부를 저장.
 */
@Entity
@Table(name = "recommendation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recommendation extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recommendation_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "intake_session_id", nullable = false)
    private IntakeSession intakeSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "symptom_id")
    private SymptomIntake symptomIntake;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(name = "department_name", nullable = false, length = 50)
    private String departmentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 10)
    private ConfidenceLevel confidenceLevel;

    @Column(name = "is_emergency", nullable = false)
    private boolean emergency;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Builder
    public Recommendation(IntakeSession intakeSession, SymptomIntake symptomIntake,
                          String department, String departmentName,
                          ConfidenceLevel confidenceLevel, boolean emergency, String reason) {
        this.publicId = PublicIdGenerator.generate("rec_");
        this.intakeSession = intakeSession;
        this.symptomIntake = symptomIntake;
        this.department = department;
        this.departmentName = departmentName;
        this.confidenceLevel = confidenceLevel;
        this.emergency = emergency;
        this.reason = reason;
    }
}
