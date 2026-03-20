package com.waddoc.domain.consultation.dto;

import com.waddoc.domain.consultation.entity.ConnectionState;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Getter
@Builder
public class ConsultationSessionStatusResponse {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private String sessionId;
    private String caseId;
    private ConsultationSessionStatus status;
    private RoomDetail room;
    private DoctorDetail doctor;
    private PatientDetail patient;
    private int reconnectCount;
    private OffsetDateTime startedAt;

    public static ConsultationSessionStatusResponse of(ConsultationSession session) {
        // webhook가 누적해 둔 세션 연결 상태를 그대로 응답 DTO에 투영한다.
        return ConsultationSessionStatusResponse.builder()
                .sessionId(session.getPublicId())
                .caseId(session.getCareCase().getPublicId())
                .status(session.getStatus())
                .room(RoomDetail.builder()
                        .roomId(session.getRoomId())
                        .livekitUrl(session.getLivekitUrl())
                        .build())
                .doctor(DoctorDetail.builder()
                        .doctorId(session.getCareCase().getDoctor().getPublicId())
                        .name(session.getCareCase().getDoctor().getUser().getName())
                        .connectionState(session.getDoctorConnectionState())
                        .joinedAt(toOffsetDateTime(session.getDoctorJoinedAt()))
                        .build())
                .patient(PatientDetail.builder()
                        .patientId(session.getCareCase().getPatient().getPublicId())
                        .name(session.getCareCase().getPatient().getName())
                        .connectionState(session.getPatientConnectionState())
                        .joinedAt(toOffsetDateTime(session.getPatientJoinedAt()))
                        .build())
                // reconnectCount는 아직 별도 저장소에서 추적하지 않아 기본값 0으로 응답한다.
                .reconnectCount(0)
                .startedAt(toOffsetDateTime(session.getStartedAt()))
                .build();
    }

    private static OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        // 엔티티는 LocalDateTime(KST 기준)으로 저장하고 있어 API 응답에서는 offset 정보를 명시한다.
        return value != null ? value.atZone(KST).toOffsetDateTime() : null;
    }

    @Getter
    @Builder
    public static class RoomDetail {
        private String roomId;
        private String livekitUrl;
    }

    @Getter
    @Builder
    public static class DoctorDetail {
        private String doctorId;
        private String name;
        private ConnectionState connectionState;
        private OffsetDateTime joinedAt;
    }

    @Getter
    @Builder
    public static class PatientDetail {
        private String patientId;
        private String name;
        private ConnectionState connectionState;
        private OffsetDateTime joinedAt;
    }
}
