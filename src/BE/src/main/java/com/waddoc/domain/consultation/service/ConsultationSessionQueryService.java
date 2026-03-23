package com.waddoc.domain.consultation.service;

import com.waddoc.domain.consultation.dto.ConsultationSessionStatusResponse;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진료 세션의 현재 상태를 읽을 때 사용하는 조회 전용 서비스다.
 */
@Service
@RequiredArgsConstructor
public class ConsultationSessionQueryService {

    private final ConsultationSessionRepository consultationSessionRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public ConsultationSessionStatusResponse getSessionStatus(String sessionId, AuthenticatedUser authenticatedUser) {
        // 상태 조회는 doctor/patient/case를 함께 내려주므로 fetch join 조회 결과를 그대로 사용한다.
        ConsultationSession session = consultationSessionRepository.findWithParticipantsByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        // 세션 자체는 공유 리소스라서, 담당 의사 또는 관리자만 현재 연결 상태를 조회할 수 있게 제한한다.
        accessControlService.assertAssignedDoctorOrAdmin(authenticatedUser, session.getCareCase());
        return ConsultationSessionStatusResponse.of(session);
    }
}
