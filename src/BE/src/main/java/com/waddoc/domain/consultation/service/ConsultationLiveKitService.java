package com.waddoc.domain.consultation.service;

import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.monitoring.LiveKitMonitoringMetrics;
import io.livekit.server.AccessToken;
import io.livekit.server.CanPublish;
import io.livekit.server.CanPublishData;
import io.livekit.server.CanSubscribe;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.RoomServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import retrofit2.Response;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * LiveKit 방 생성과 의사/환자 참가 토큰 발급을 캡슐화한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationLiveKitService {

    private static final long PARTICIPANT_TOKEN_TTL_MILLIS = TimeUnit.HOURS.toMillis(2);
    private static final int PARTICIPANT_TOKEN_EXPIRES_IN_SECONDS = 7200;

    private final RoomServiceClient roomServiceClient;
    private final LiveKitMonitoringMetrics liveKitMonitoringMetrics;

    @Value("${livekit.api-key}")
    private String apiKey;

    @Value("${livekit.api-secret}")
    private String apiSecret;

    @Value("${livekit.url}")
    private String livekitUrl;

    public void createRoom(String roomId) {
        liveKitMonitoringMetrics.recordRoomOperation("create", () -> {
            try {
                Response<?> response = roomServiceClient.createRoom(roomId).execute();
                if (!response.isSuccessful()) {
                    log.error("LiveKit room creation failed. roomId={}, code={}", roomId, response.code());
                    throw new BusinessException(ErrorCode.LIVEKIT_ROOM_CREATE_FAILED);
                }
            } catch (IOException e) {
                log.error("LiveKit room creation failed due to IO error. roomId={}", roomId, e);
                throw new BusinessException(ErrorCode.LIVEKIT_ROOM_CREATE_FAILED);
            }
        });
    }

    public void deleteRoom(String roomId) {
        liveKitMonitoringMetrics.recordRoomOperation("delete", () -> {
            try {
                Response<Void> response = roomServiceClient.deleteRoom(roomId).execute();
                if (response.isSuccessful() || response.code() == 404) {
                    return;
                }

                log.error("LiveKit room deletion failed. roomId={}, code={}", roomId, response.code());
                throw new BusinessException(ErrorCode.LIVEKIT_ROOM_CREATE_FAILED);
            } catch (IOException e) {
                log.error("LiveKit room deletion failed due to IO error. roomId={}", roomId, e);
                throw new BusinessException(ErrorCode.LIVEKIT_ROOM_CREATE_FAILED);
            }
        });
    }

    public String issueDoctorToken(ConsultationSession session, DoctorProfile doctorProfile) {
        return liveKitMonitoringMetrics.recordTokenIssuance("doctor", () -> {
            AccessToken accessToken = new AccessToken(apiKey, apiSecret);
            accessToken.setIdentity("doctor:" + doctorProfile.getPublicId());
            accessToken.setName(doctorProfile.getUser().getName());
            accessToken.setTtl(PARTICIPANT_TOKEN_TTL_MILLIS);
            accessToken.addGrants(
                    new RoomJoin(true),
                    new RoomName(session.getRoomId()),
                    new CanPublish(true),
                    new CanSubscribe(true),
                    new CanPublishData(true)
            );
            return accessToken.toJwt();
        });
    }

    public String issuePatientToken(ConsultationSession session, Patient patient) {
        return liveKitMonitoringMetrics.recordTokenIssuance("patient", () -> {
            AccessToken accessToken = new AccessToken(apiKey, apiSecret);
            // webhook에서 참가자 구분에 쓰는 identity 규칙과 동일하게 맞춘다.
            accessToken.setIdentity("patient:" + patient.getPublicId());
            accessToken.setName(patient.getName());
            accessToken.setTtl(PARTICIPANT_TOKEN_TTL_MILLIS);
            accessToken.addGrants(
                    new RoomJoin(true),
                    new RoomName(session.getRoomId()),
                    new CanPublish(true),
                    new CanSubscribe(true),
                    new CanPublishData(true)
            );
            return accessToken.toJwt();
        });
    }

    public int getParticipantTokenExpiresInSeconds() {
        return PARTICIPANT_TOKEN_EXPIRES_IN_SECONDS;
    }

    public String getLivekitUrl() {
        return livekitUrl;
    }
}
