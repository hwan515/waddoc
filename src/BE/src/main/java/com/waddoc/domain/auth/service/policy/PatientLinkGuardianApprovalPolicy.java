package com.waddoc.domain.auth.service.policy;

import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.repository.PatientGuardianLinkRepository;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.type.ApprovalStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PatientLinkGuardianApprovalPolicy implements GuardianApprovalPolicy {

    private final PatientGuardianLinkRepository patientGuardianLinkRepository;

    @Override
    public void validateApprovedGuardian(User user) {
        if (user.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new BusinessException(ErrorCode.AUTH_GUARDIAN_NOT_APPROVED);
        }
        if (!patientGuardianLinkRepository.existsByGuardianUserIdAndStatus(user.getId(), GuardianLinkStatus.APPROVED)) {
            throw new BusinessException(ErrorCode.AUTH_GUARDIAN_NOT_APPROVED);
        }
    }
}
