package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.mission.dto.IssueMissionTerminalTokenResponse;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.repository.MissionRepository;
import com.waddoc.domain.user.entity.Role;
import com.waddoc.global.security.AuthenticatedUser;
import com.waddoc.global.security.authorization.AccessControlService;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import com.waddoc.global.security.jwt.MissionTerminalScopes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MissionTerminalTokenServiceTest {

    @Mock
    private MissionRepository missionRepository;

    @Mock
    private AccessControlService accessControlService;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private MissionTerminalTokenService missionTerminalTokenService;

    @Test
    void issueToken_returnsMissionScopedTerminalToken() {
        AuthenticatedUser doctor = new AuthenticatedUser("usr_doctor", Role.DOCTOR);
        CareCase careCase = mock(CareCase.class);
        when(careCase.getPublicId()).thenReturn("case_test123");

        Mission mission = Mission.builder()
                .careCase(careCase)
                .vehicleId("VEH-01")
                .destination("서울시 강남구")
                .build();
        setField(mission, "publicId", "ms_test123");

        when(missionRepository.findWithDetailsByPublicId("ms_test123")).thenReturn(Optional.of(mission));
        when(jwtTokenProvider.createMissionTerminalToken(
                eq("ms_test123"),
                eq("case_test123"),
                eq(List.of(
                        MissionTerminalScopes.IDENTITY_CHECK,
                        MissionTerminalScopes.ISSUE_PATIENT_TOKEN,
                        MissionTerminalScopes.SESSION_STATUS_READ,
                        MissionTerminalScopes.VITALS_WRITE
                ))
        )).thenReturn("mission-terminal-token");
        when(jwtTokenProvider.getMissionTerminalTokenExpiry()).thenReturn(1800L);

        IssueMissionTerminalTokenResponse response = missionTerminalTokenService.issueToken("ms_test123", doctor);

        assertThat(response.getMissionId()).isEqualTo("ms_test123");
        assertThat(response.getCaseId()).isEqualTo("case_test123");
        assertThat(response.getTerminalToken()).isEqualTo("mission-terminal-token");
        assertThat(response.getExpiresIn()).isEqualTo(1800L);
        assertThat(response.getScopes()).containsExactly(
                MissionTerminalScopes.IDENTITY_CHECK,
                MissionTerminalScopes.ISSUE_PATIENT_TOKEN,
                MissionTerminalScopes.SESSION_STATUS_READ,
                MissionTerminalScopes.VITALS_WRITE
        );
        verify(accessControlService).assertAssignedDoctorOrAdmin(doctor, careCase);
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(), any());
    }

    private void setField(Object target, String fieldName, Object value) {
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("Field not found: " + fieldName);
    }
}
