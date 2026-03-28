package com.waddoc.global.security.authorization;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.repository.DoctorProfileRepository;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.patient.repository.PatientRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.DeviceTerminalPrincipal;
import com.waddoc.global.security.MissionTerminalPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccessControlService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final PatientGuardianLinkRepository patientGuardianLinkRepository;
    private final PatientRepository patientRepository;

    public void assertAdmin(AuthenticatedUser authenticatedUser) {
        assertAuthenticated(authenticatedUser);
        if (authenticatedUser.role() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    public DoctorProfile getDoctorProfileOrThrow(AuthenticatedUser authenticatedUser) {
        assertAuthenticated(authenticatedUser);
        if (authenticatedUser.role() != Role.DOCTOR) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
        return doctorProfileRepository.findByUserPublicId(authenticatedUser.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_DOCTOR_PROFILE_REQUIRED));
    }

    public void assertAssignedDoctorOrAdmin(AuthenticatedUser authenticatedUser, CareCase careCase) {
        assertAuthenticated(authenticatedUser);
        if (authenticatedUser.role() == Role.ADMIN) {
            return;
        }

        DoctorProfile doctorProfile = getDoctorProfileOrThrow(authenticatedUser);
        if (!doctorProfile.getId().equals(careCase.getDoctor().getId())) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    public void assertGuardian(AuthenticatedUser authenticatedUser) {
        assertAuthenticated(authenticatedUser);
        if (authenticatedUser.role() != Role.GUARDIAN) {
            throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
        }
    }

    public Patient getGuardianLinkedPatientOrThrow(AuthenticatedUser authenticatedUser, String patientPublicId) {
        assertGuardian(authenticatedUser);

        boolean linked = patientGuardianLinkRepository.existsByPatientPublicIdAndGuardianUserPublicIdAndStatus(
                patientPublicId,
                authenticatedUser.userId(),
                GuardianLinkStatus.APPROVED
        );
        if (!linked) {
            throw new BusinessException(ErrorCode.GUARDIAN_NOT_LINKED);
        }

        return patientRepository.findByPublicId(patientPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PATIENT_NOT_FOUND));
    }

    public AccessActor assertAdminOrMissionTerminal(
            Authentication authentication,
            String missionId,
            String requiredScope
    ) {
        assertAuthenticated(authentication);

        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            assertAdmin(authenticatedUser);
            return new AccessActor(authenticatedUser.userId(), authenticatedUser.role().name());
        }

        if (principal instanceof MissionTerminalPrincipal missionTerminalPrincipal) {
            if (!missionTerminalPrincipal.missionId().equals(missionId)
                    || !missionTerminalPrincipal.hasScope(requiredScope)) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
            }
            return new AccessActor(missionTerminalPrincipal.subject(), missionTerminalPrincipal.actorRole());
        }

        throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }

    public AccessActor assertDeviceTerminal(Authentication authentication, String requiredScope) {
        DeviceTerminalPrincipal deviceTerminalPrincipal = assertDeviceTerminalPrincipal(authentication, requiredScope);
        return new AccessActor(deviceTerminalPrincipal.terminalId(), deviceTerminalPrincipal.actorRole());
    }

    public DeviceTerminalPrincipal assertDeviceTerminalPrincipal(Authentication authentication, String requiredScope) {
        assertAuthenticated(authentication);

        Object principal = authentication.getPrincipal();
        if (principal instanceof DeviceTerminalPrincipal deviceTerminalPrincipal) {
            if (!deviceTerminalPrincipal.hasScope(requiredScope)) {
                throw new BusinessException(ErrorCode.AUTH_FORBIDDEN);
            }
            return deviceTerminalPrincipal;
        }

        throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
    }

    private void assertAuthenticated(AuthenticatedUser authenticatedUser) {
        if (authenticatedUser == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }

    private void assertAuthenticated(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHORIZED);
        }
    }
}
