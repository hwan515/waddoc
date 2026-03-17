package com.waddoc.domain.admin.service;

import com.waddoc.domain.admin.dto.*;
import com.waddoc.domain.auth.service.RefreshTokenService;
import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.carecase.repository.CareCaseRepository;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.repository.ConsultationSessionRepository;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.domain.user.entity.User;
import com.waddoc.domain.user.repository.UserRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
public class AdminService {

    private final AccessControlService accessControlService;
    private final BookingRepository bookingRepository;
    private final CareCaseRepository careCaseRepository;
    private final MissionRepository missionRepository;
    private final ConsultationSessionRepository consultationSessionRepository;
    private final PatientRepository patientRepository;
    private final PatientGuardianLinkRepository patientGuardianLinkRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    @Transactional(readOnly = true)
    public AdminBookingListResponse getBookings(
            AuthenticatedUser authenticatedUser,
            LocalDate date,
            BookingStatus status,
            int page,
            int size
    ) {
        accessControlService.assertAdmin(authenticatedUser);
        Page<Booking> bookingPage = bookingRepository.searchAdminBookings(date, status, pageRequest(page, size));
        if (bookingPage.isEmpty()) {
            return AdminBookingListResponse.of(Collections.emptyList(), bookingPage);
        }

        List<CareCase> careCases = careCaseRepository.findAllByBookingIn(bookingPage.getContent());
        Map<Long, CareCase> careCaseByBookingId = careCases.stream()
                .collect(Collectors.toMap(careCase -> careCase.getBooking().getId(), Function.identity()));
        Map<Long, MissionPhase> missionPhaseByCaseId = missionRepository.findAllByCareCaseIn(careCases).stream()
                .collect(Collectors.toMap(mission -> mission.getCareCase().getId(), Mission::getPhase));

        List<AdminBookingSummaryResponse> responses = bookingPage.getContent().stream()
                .map(booking -> {
                    CareCase careCase = careCaseByBookingId.get(booking.getId());
                    MissionPhase missionPhase = careCase != null ? missionPhaseByCaseId.get(careCase.getId()) : null;
                    return AdminBookingSummaryResponse.from(
                            booking,
                            careCase != null ? careCase.getPublicId() : null,
                            missionPhase
                    );
                })
                .toList();

        return AdminBookingListResponse.of(responses, bookingPage);
    }

    @Transactional(readOnly = true)
    public AdminCaseListResponse getCases(
            AuthenticatedUser authenticatedUser,
            LocalDate date,
            CaseStatus status,
            int page,
            int size
    ) {
        accessControlService.assertAdmin(authenticatedUser);
        Page<CareCase> casePage = careCaseRepository.searchAdminCases(date, status, pageRequest(page, size));
        if (casePage.isEmpty()) {
            return AdminCaseListResponse.of(Collections.emptyList(), casePage);
        }

        Map<Long, MissionPhase> missionPhaseByCaseId = missionRepository.findAllByCareCaseIn(casePage.getContent()).stream()
                .collect(Collectors.toMap(mission -> mission.getCareCase().getId(), Mission::getPhase));
        Map<Long, ConsultationSessionStatus> sessionStatusByCaseId = consultationSessionRepository.findAllByCareCaseIn(casePage.getContent()).stream()
                .collect(Collectors.toMap(session -> session.getCareCase().getId(), ConsultationSession::getStatus));

        List<AdminCaseSummaryResponse> responses = casePage.getContent().stream()
                .map(careCase -> AdminCaseSummaryResponse.from(
                        careCase,
                        missionPhaseByCaseId.get(careCase.getId()),
                        sessionStatusByCaseId.get(careCase.getId())
                ))
                .toList();

        return AdminCaseListResponse.of(responses, casePage);
    }

    @Transactional(readOnly = true)
    public AdminSessionListResponse getSessions(
            AuthenticatedUser authenticatedUser,
            LocalDate date,
            ConsultationSessionStatus status,
            int page,
            int size
    ) {
        accessControlService.assertAdmin(authenticatedUser);
        Page<ConsultationSession> sessionPage = consultationSessionRepository.searchAdminSessions(
                date,
                status,
                pageRequest(page, size)
        );

        List<AdminSessionSummaryResponse> responses = sessionPage.getContent().stream()
                .map(AdminSessionSummaryResponse::from)
                .toList();

        return AdminSessionListResponse.of(responses, sessionPage);
    }

    @Transactional(readOnly = true)
    public AdminPatientListResponse getPatients(
            AuthenticatedUser authenticatedUser,
            String name,
            String phone,
            int page,
            int size
    ) {
        accessControlService.assertAdmin(authenticatedUser);
        Page<Patient> patientPage = patientRepository.searchAdminPatients(
                normalize(name),
                normalize(phone),
                pageRequest(page, size)
        );

        List<AdminPatientSummaryResponse> responses = patientPage.getContent().stream()
                .map(AdminPatientSummaryResponse::from)
                .toList();

        return AdminPatientListResponse.of(responses, patientPage);
    }

    @Transactional(readOnly = true)
    public GuardianLinkRequestListResponse getGuardianLinkRequests(
            AuthenticatedUser authenticatedUser,
            GuardianLinkStatus status,
            int page,
            int size
    ) {
        accessControlService.assertAdmin(authenticatedUser);
        Page<PatientGuardianLink> requestPage = patientGuardianLinkRepository.searchAdminGuardianLinkRequests(
                status,
                pageRequest(page, size)
        );

        List<GuardianLinkRequestSummaryResponse> responses = requestPage.getContent().stream()
                .map(GuardianLinkRequestSummaryResponse::from)
                .toList();

        return GuardianLinkRequestListResponse.of(responses, requestPage);
    }

    @Transactional
    public GuardianLinkApprovalResponse approveGuardianLinkRequest(
            AuthenticatedUser authenticatedUser,
            String linkId
    ) {
        User adminUser = getAdminUser(authenticatedUser);
        PatientGuardianLink link = patientGuardianLinkRepository.findDetailedByPublicId(linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_LINK_REQUEST_NOT_FOUND));

        validatePending(link);
        link.approve(adminUser);
        link.getGuardianUser().approve(adminUser);

        return GuardianLinkApprovalResponse.from(link);
    }

    @Transactional
    public GuardianLinkRejectionResponse rejectGuardianLinkRequest(
            AuthenticatedUser authenticatedUser,
            String linkId
    ) {
        User adminUser = getAdminUser(authenticatedUser);
        PatientGuardianLink link = patientGuardianLinkRepository.findDetailedByPublicId(linkId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GUARDIAN_LINK_REQUEST_NOT_FOUND));

        validatePending(link);
        link.reject(adminUser);

        boolean hasApprovedLink = patientGuardianLinkRepository.existsByGuardianUserIdAndStatus(
                link.getGuardianUser().getId(),
                GuardianLinkStatus.APPROVED
        );
        if (!hasApprovedLink) {
            link.getGuardianUser().reject(adminUser);
            refreshTokenService.deleteAllByUserId(link.getGuardianUser().getPublicId());
        }

        return GuardianLinkRejectionResponse.from(link);
    }

    private User getAdminUser(AuthenticatedUser authenticatedUser) {
        accessControlService.assertAdmin(authenticatedUser);
        return userRepository.findByPublicId(authenticatedUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
    }

    private void validatePending(PatientGuardianLink link) {
        if (link.getStatus() != GuardianLinkStatus.PENDING) {
            throw new BusinessException(ErrorCode.GUARDIAN_LINK_ALREADY_PROCESSED);
        }
    }

    private Pageable pageRequest(int page, int size) {
        return PageRequest.of(page, size);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
