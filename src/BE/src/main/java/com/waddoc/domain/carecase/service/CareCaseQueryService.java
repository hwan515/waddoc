package com.waddoc.domain.carecase.service;

import com.waddoc.domain.carecase.dto.CaseDetailResponse;
import com.waddoc.domain.carecase.dto.DoctorCaseListResponse;
import com.waddoc.domain.carecase.dto.DoctorCaseSummaryResponse;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareCaseQueryService {

    private final CareCaseRepository careCaseRepository;
    private final MissionRepository missionRepository;
    private final ConsultationSessionRepository consultationSessionRepository;
    private final AccessControlService accessControlService;

    public CaseDetailResponse getCaseDetail(String caseId, AuthenticatedUser authenticatedUser) {
        CareCase careCase = careCaseRepository.findWithDetailsByPublicId(caseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CASE_NOT_FOUND));

        accessControlService.assertAssignedDoctorOrAdmin(authenticatedUser, careCase);

        Mission mission = missionRepository.findByCareCase(careCase).orElse(null);
        ConsultationSession session = consultationSessionRepository.findByCareCase(careCase).orElse(null);
        return CaseDetailResponse.of(careCase, mission, session);
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

        Map<Long, MissionPhase> missionPhaseByCaseId = missionRepository.findAllByCareCaseIn(careCases).stream()
                .collect(Collectors.toMap(
                        mission -> mission.getCareCase().getId(),
                        Mission::getPhase
                ));

        List<DoctorCaseSummaryResponse> responses = careCases.stream()
                .map(careCase -> DoctorCaseSummaryResponse.from(
                        careCase,
                        missionPhaseByCaseId.get(careCase.getId())
                ))
                .toList();

        return DoctorCaseListResponse.of(responses);
    }
}
