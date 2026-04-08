package com.waddoc.domain.auth.service.policy;

import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GuardianLoginEligibilityValidator implements LoginEligibilityValidator {

    private final GuardianApprovalPolicy guardianApprovalPolicy;

    @Override
    public Role supports() {
        return Role.GUARDIAN;
    }

    @Override
    public void validate(User user) {
        guardianApprovalPolicy.validateApprovedGuardian(user);
    }
}
