package com.waddoc.domain.auth.service;

import com.waddoc.domain.auth.service.policy.LoginEligibilityValidator;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class LoginEligibilityService {

    private final Map<Role, LoginEligibilityValidator> validators;

    public LoginEligibilityService(List<LoginEligibilityValidator> validators) {
        this.validators = new EnumMap<>(Role.class);
        validators.forEach(validator -> this.validators.put(validator.supports(), validator));
    }

    public void validate(User user) {
        LoginEligibilityValidator validator = validators.get(user.getRole());
        if (validator == null) {
            throw new IllegalStateException("No login eligibility validator configured for role: " + user.getRole());
        }
        validator.validate(user);
    }
}
