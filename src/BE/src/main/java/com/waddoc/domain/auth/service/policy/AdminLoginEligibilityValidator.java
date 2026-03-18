package com.waddoc.domain.auth.service.policy;

import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import org.springframework.stereotype.Component;

@Component
public class AdminLoginEligibilityValidator implements LoginEligibilityValidator {

    @Override
    public Role supports() {
        return Role.ADMIN;
    }

    @Override
    public void validate(User user) {
        // ADMIN은 USER 활성 상태만 통과하면 추가 선행조건이 없다.
    }
}
