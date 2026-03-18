package com.waddoc.domain.consultation.entity;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.global.audit.BaseCreatedEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "consultation_session")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsultationSession extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false, unique = true)
    private CareCase careCase;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConsultationSessionStatus status;

    @Column(name = "room_id", length = 100)
    private String roomId;

    @Column(name = "livekit_url", length = 255)
    private String livekitUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "doctor_connection_state", nullable = false, length = 20)
    private ConnectionState doctorConnectionState;

    @Enumerated(EnumType.STRING)
    @Column(name = "patient_connection_state", nullable = false, length = 20)
    private ConnectionState patientConnectionState;

    @Column(name = "doctor_joined_at")
    private LocalDateTime doctorJoinedAt;

    @Column(name = "patient_joined_at")
    private LocalDateTime patientJoinedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Builder
    public ConsultationSession(CareCase careCase, String roomId, String livekitUrl) {
        this.publicId = PublicIdGenerator.generate("ses_");
        this.careCase = careCase;
        this.status = ConsultationSessionStatus.CREATED;
        this.roomId = roomId;
        this.livekitUrl = livekitUrl;
        this.doctorConnectionState = ConnectionState.DISCONNECTED;
        this.patientConnectionState = ConnectionState.DISCONNECTED;
    }

    public void markReady() {
        this.status = ConsultationSessionStatus.READY;
    }

    public void start() {
        this.status = ConsultationSessionStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    public void complete(int durationMinutes) {
        this.status = ConsultationSessionStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
        this.durationMinutes = durationMinutes;
    }

    public void connectDoctor() {
        this.doctorConnectionState = ConnectionState.CONNECTED;
        if (this.doctorJoinedAt == null) {
            this.doctorJoinedAt = LocalDateTime.now();
        }
        transitionToInProgressIfReady();
    }

    public void connectPatient() {
        this.patientConnectionState = ConnectionState.CONNECTED;
        if (this.patientJoinedAt == null) {
            this.patientJoinedAt = LocalDateTime.now();
        }
        transitionToInProgressIfReady();
    }

    public void disconnectDoctor() {
        this.doctorConnectionState = ConnectionState.DISCONNECTED;
    }

    public void disconnectPatient() {
        this.patientConnectionState = ConnectionState.DISCONNECTED;
    }

    public boolean isDoctorConnected() {
        return this.doctorConnectionState == ConnectionState.CONNECTED;
    }

    public boolean isPatientConnected() {
        return this.patientConnectionState == ConnectionState.CONNECTED;
    }

    private void transitionToInProgressIfReady() {
        if (this.status == ConsultationSessionStatus.CREATED) {
            markReady();
        }
        if (this.status == ConsultationSessionStatus.READY
                && this.doctorConnectionState == ConnectionState.CONNECTED
                && this.patientConnectionState == ConnectionState.CONNECTED) {
            start();
        }
    }
}
