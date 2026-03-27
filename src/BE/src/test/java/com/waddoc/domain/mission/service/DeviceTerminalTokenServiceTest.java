package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.mission.config.RobotTerminalRegistry;
import com.waddoc.domain.mission.dto.DeviceTerminalBootstrapRequest;
import com.waddoc.domain.mission.dto.DeviceTerminalBootstrapResponse;
import com.waddoc.global.security.jwt.DeviceTerminalScopes;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTerminalTokenServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuditLogService auditLogService;

    @Test
    void bootstrap_returnsDeviceTokenBoundToVehicleAndRegion() {
        RobotTerminalRegistry robotTerminalRegistry = new RobotTerminalRegistry(
                "robot-terminal-01|bootstrap-key|veh_GIMCHEON_01|GIMCHEON"
        );
        DeviceTerminalTokenService deviceTerminalTokenService = new DeviceTerminalTokenService(
                jwtTokenProvider,
                auditLogService,
                robotTerminalRegistry
        );

        DeviceTerminalBootstrapRequest request = new DeviceTerminalBootstrapRequest();
        setField(request, "terminalId", "robot-terminal-01");
        setField(request, "terminalKey", "bootstrap-key");

        when(jwtTokenProvider.createDeviceTerminalToken(
                "robot-terminal-01",
                "veh_GIMCHEON_01",
                "GIMCHEON",
                List.of(
                        DeviceTerminalScopes.READ_CURRENT_MISSION,
                        DeviceTerminalScopes.CHECK_IN_CANDIDATES,
                        DeviceTerminalScopes.CLAIM_MISSION
                )
        )).thenReturn("device-terminal-token");
        when(jwtTokenProvider.getDeviceTerminalTokenExpiry()).thenReturn(1800L);

        DeviceTerminalBootstrapResponse response = deviceTerminalTokenService.bootstrap(request);

        assertThat(response.getTerminalId()).isEqualTo("robot-terminal-01");
        assertThat(response.getVehicleId()).isEqualTo("veh_GIMCHEON_01");
        assertThat(response.getRegionCode()).isEqualTo("GIMCHEON");
        assertThat(response.getDeviceTerminalToken()).isEqualTo("device-terminal-token");
        assertThat(response.getScopes()).containsExactly(
                DeviceTerminalScopes.READ_CURRENT_MISSION,
                DeviceTerminalScopes.CHECK_IN_CANDIDATES,
                DeviceTerminalScopes.CLAIM_MISSION
        );
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
