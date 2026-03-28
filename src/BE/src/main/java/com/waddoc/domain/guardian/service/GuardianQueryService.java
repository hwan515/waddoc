package com.waddoc.domain.guardian.service;

import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import com.waddoc.domain.consultation.repository.ConsultationSummaryRepository;
import com.waddoc.domain.guardian.dto.GuardianConsultationSummariesResponse;
import com.waddoc.domain.guardian.dto.GuardianConsultationSummaryResponse;
import com.waddoc.domain.guardian.dto.GuardianPatientResponse;
import com.waddoc.domain.guardian.dto.GuardianPatientsResponse;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 보호자가 조회할 수 있는 환자 목록과 진료 요약을 읽어 오는 서비스다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GuardianQueryService {

    private final PatientGuardianLinkRepository patientGuardianLinkRepository;
    private final ConsultationSummaryRepository consultationSummaryRepository;
    private final AccessControlService accessControlService;

    public GuardianPatientsResponse getLinkedPatients(AuthenticatedUser authenticatedUser) {
        accessControlService.assertGuardian(authenticatedUser);

        List<PatientGuardianLink> links = patientGuardianLinkRepository.findAllByGuardianUserPublicIdAndStatus(
                authenticatedUser.userId(),
                GuardianLinkStatus.APPROVED
        );

        List<GuardianPatientResponse> patients = links.stream()
                .map(GuardianPatientResponse::from)
                .toList();

        return GuardianPatientsResponse.of(patients);
    }

    public GuardianConsultationSummariesResponse getConsultationSummaries(
            AuthenticatedUser authenticatedUser,
            String patientId
    ) {
        Patient patient = accessControlService.getGuardianLinkedPatientOrThrow(authenticatedUser, patientId);
        List<ConsultationSummary> summaries = consultationSummaryRepository.findAllByPatientPublicIdAndSessionStatus(
                patientId,
                ConsultationSessionStatus.COMPLETED
        );

        List<GuardianConsultationSummaryResponse> responses = summaries.stream()
                .map(GuardianConsultationSummaryResponse::from)
                .toList();

        return GuardianConsultationSummariesResponse.of(patient.getName(), responses);
    }
}
