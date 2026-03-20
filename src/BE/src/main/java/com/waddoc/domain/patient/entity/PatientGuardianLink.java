package com.waddoc.domain.patient.entity;

import com.waddoc.domain.user.entity.User;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "patient_guardian_link",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_patient_guardian_link_pair",
                columnNames = {"patient_id", "guardian_user_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientGuardianLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "link_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guardian_user_id", nullable = false)
    private User guardianUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedByUser;

    @Column(length = 30)
    private String relation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GuardianLinkStatus status;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Builder
    public PatientGuardianLink(Patient patient, User guardianUser, String relation) {
        this.publicId = PublicIdGenerator.generate("link_");
        this.patient = patient;
        this.guardianUser = guardianUser;
        this.relation = relation;
        this.status = GuardianLinkStatus.PENDING;
        this.requestedAt = LocalDateTime.now();
    }

    public void approve(User approver) {
        this.status = GuardianLinkStatus.APPROVED;
        this.approvedByUser = approver;
        this.approvedAt = LocalDateTime.now();
    }

    public void reject(User approver) {
        this.status = GuardianLinkStatus.REJECTED;
        this.approvedByUser = approver;
        this.approvedAt = LocalDateTime.now();
    }
}
