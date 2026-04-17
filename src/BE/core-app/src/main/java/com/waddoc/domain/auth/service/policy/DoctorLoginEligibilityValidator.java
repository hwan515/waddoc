package com.waddoc.domain.auth.service.policy;

import com.waddoc.domain.doctor.repository.DoctorProfileRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.domain.user.entity.User;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DoctorLoginEligibilityValidator implements LoginEligibilityValidator {

    private final DoctorProfileRepository doctorProfileRepository;

    @Override
    public Role supports() {
        return Role.DOCTOR;
    }

    @Override
    public void validate(User user) {
        if (!doctorProfileRepository.existsByUserId(user.getId())) {
            throw new BusinessException(ErrorCode.AUTH_DOCTOR_PROFILE_REQUIRED);
        }
    }
}
