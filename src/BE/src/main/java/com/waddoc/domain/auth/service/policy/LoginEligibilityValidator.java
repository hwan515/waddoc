package com.waddoc.domain.auth.service.policy;

import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;

public interface LoginEligibilityValidator {

    Role supports();

    void validate(User user);
}
