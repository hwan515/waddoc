package com.waddoc.domain.auth.service.policy;

import com.waddoc.domain.user.entity.User;

public interface GuardianApprovalPolicy {

    void validateApprovedGuardian(User user);
}
