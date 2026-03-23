package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.mission.config.RobotTerminalRegistry;
import com.waddoc.domain.mission.dto.DeviceTerminalBootstrapRequest;
import com.waddoc.domain.mission.dto.DeviceTerminalBootstrapResponse;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.jwt.DeviceTerminalScopes;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 등록된 차량 단말이 처음 붙을 때 사용할 bootstrap 토큰을 발급한다.
 */
@Service
public class DeviceTerminalTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;
    private final RobotTerminalRegistry robotTerminalRegistry;

    public DeviceTerminalTokenService(
            JwtTokenProvider jwtTokenProvider,
            AuditLogService auditLogService,
            RobotTerminalRegistry robotTerminalRegistry
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogService = auditLogService;
        this.robotTerminalRegistry = robotTerminalRegistry;
    }

    public DeviceTerminalBootstrapResponse bootstrap(DeviceTerminalBootstrapRequest request) {
        if (!robotTerminalRegistry.isConfigured()) {
            throw new BusinessException(ErrorCode.AUTH_TERMINAL_BOOTSTRAP_DISABLED);
        }

        String requestedTerminalId = request.getTerminalId().trim();
        RobotTerminalRegistry.TerminalRegistration registration = robotTerminalRegistry.findByTerminalId(requestedTerminalId)
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (!registration.matchesKey(request.getTerminalKey())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        List<String> scopes = List.of(
                DeviceTerminalScopes.CHECK_IN_CANDIDATES,
                DeviceTerminalScopes.CLAIM_MISSION
        );
        String deviceTerminalToken = jwtTokenProvider.createDeviceTerminalToken(
                requestedTerminalId,
                registration.vehicleId(),
                registration.regionCode(),
                scopes
        );

        auditLogService.log(
                "DEVICE_TERMINAL_BOOTSTRAPPED",
                "TERMINAL",
                requestedTerminalId,
                "corr_terminal_" + requestedTerminalId,
                requestedTerminalId,
                "DEVICE_TERMINAL",
                Map.of(
                        "vehicleId", registration.vehicleId() != null ? registration.vehicleId() : "",
                        "regionCode", registration.regionCode() != null ? registration.regionCode() : "",
                        "expiresInSeconds", jwtTokenProvider.getDeviceTerminalTokenExpiry(),
                        "scopes", scopes
                )
        );

        return DeviceTerminalBootstrapResponse.of(
                requestedTerminalId,
                registration.vehicleId(),
                registration.regionCode(),
                deviceTerminalToken,
                jwtTokenProvider.getDeviceTerminalTokenExpiry(),
                scopes
        );
    }
}
