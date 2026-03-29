package com.waddoc.domain.carecase.service;

import com.waddoc.domain.carecase.dto.CaseDetailResponse;
import com.waddoc.domain.carecase.dto.DoctorCaseListResponse;
import com.waddoc.domain.carecase.dto.DoctorCaseSummaryResponse;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.consultation.repository.ConsultationSummaryRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.vital.dto.VitalMeasurementResponse;
import com.waddoc.domain.vital.repository.VitalMeasurementRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 케이스 상세와 담당 의사 목록 조회에 필요한 데이터 조합과 권한 검사를 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareCaseQueryService {

    private final CareCaseRepository careCaseRepository;
    private final MissionRepository missionRepository;
    private final ConsultationSessionRepository consultationSessionRepository;
    private final ConsultationSummaryRepository consultationSummaryRepository;
    private final VitalMeasurementRepository vitalMeasurementRepository;
    private final AccessControlService accessControlService;

    public CaseDetailResponse getCaseDetail(String caseId, AuthenticatedUser authenticatedUser) {
        // 케이스 상세는 예약/환자/의사 정보까지 필요해서 fetch join 메서드로 읽는다.
        CareCase careCase = careCaseRepository.findWithDetailsByPublicId(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));

        accessControlService.assertAssignedDoctorOrAdmin(authenticatedUser, careCase);

        // 미션/세션은 아직 생성 전일 수 있으므로 없으면 null로 내려준다.
        Mission mission = missionRepository.findByCareCase(careCase).orElse(null);
        ConsultationSession session = consultationSessionRepository.findByCareCase(careCase).orElse(null);
        List<ConsultationSummary> consultationHistories =
                consultationSummaryRepository.findAllByPatientPublicIdAndSessionStatus(
                        careCase.getPatient().getPublicId(),
                        ConsultationSessionStatus.COMPLETED
                );
        VitalMeasurementResponse vitals = vitalMeasurementRepository.findByCareCase(careCase)
                .map(VitalMeasurementResponse::from)
                .orElse(null);
        return CaseDetailResponse.of(careCase, mission, session, consultationHistories, vitals);
    }

    public DoctorCaseListResponse getAssignedCases(
            AuthenticatedUser authenticatedUser,
            CaseStatus status,
            LocalDate appointmentDate
    ) {
        accessControlService.getDoctorProfileOrThrow(authenticatedUser);

        List<CareCase> careCases = careCaseRepository.findAllAssignedToDoctor(
                authenticatedUser.userId(),
                status,
                appointmentDate
        );

        if (careCases.isEmpty()) {
            return DoctorCaseListResponse.of(Collections.emptyList());
        }

        // 목록 응답에서 케이스마다 미션을 다시 조회하지 않도록 한 번에 맵으로 모은다.
        Map<Long, MissionPhase> missionPhaseByCaseId = missionRepository.findAllByCareCaseIn(careCases).stream()
                .collect(Collectors.toMap(
                        mission -> mission.getCareCase().getId(),
                        Mission::getPhase
                ));
        Map<Long, ConsultationSession> consultationSessionByCaseId =
                consultationSessionRepository.findAllByCareCaseIn(careCases).stream()
                        .collect(Collectors.toMap(
                                session -> session.getCareCase().getId(),
                                Function.identity()
                        ));

        List<DoctorCaseSummaryResponse> responses = careCases.stream()
                .map(careCase -> DoctorCaseSummaryResponse.from(
                        careCase,
                        missionPhaseByCaseId.get(careCase.getId()),
                        consultationSessionByCaseId.get(careCase.getId())
                ))
                .toList();

        return DoctorCaseListResponse.of(responses);
    }
}
