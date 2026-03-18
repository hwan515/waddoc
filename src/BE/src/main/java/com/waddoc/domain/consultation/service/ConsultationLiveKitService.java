package com.waddoc.domain.consultation.service;

import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationLiveKitService {

    private final RoomServiceClient roomServiceClient;

    @Value("${livekit.api-key}")
    private String apiKey;

    @Value("${livekit.api-secret}")
    private String apiSecret;

    @Value("${livekit.url}")
    private String livekitUrl;

    public void createRoom(String roomId) {
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
    }

    public String issueDoctorToken(ConsultationSession session, DoctorProfile doctorProfile) {
        AccessToken accessToken = new AccessToken(apiKey, apiSecret);
        accessToken.setIdentity("doctor:" + doctorProfile.getPublicId());
        accessToken.setName(doctorProfile.getUser().getName());
        accessToken.setTtl(TimeUnit.HOURS.toMillis(2));
        accessToken.addGrants(
                new RoomJoin(true),
                new RoomName(session.getRoomId()),
                new CanPublish(true),
                new CanSubscribe(true),
                new CanPublishData(true)
        );
        return accessToken.toJwt();
    }

    public String getLivekitUrl() {
        return livekitUrl;
    }
}
