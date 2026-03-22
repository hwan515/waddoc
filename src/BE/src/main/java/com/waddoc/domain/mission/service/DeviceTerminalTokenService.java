package com.waddoc.domain.mission.service;

import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.mission.dto.DeviceTerminalBootstrapRequest;
import com.waddoc.domain.mission.dto.DeviceTerminalBootstrapResponse;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import com.waddoc.global.security.jwt.DeviceTerminalScopes;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class DeviceTerminalTokenService {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;
    private final String bootstrapId;
    private final String bootstrapKey;

    public DeviceTerminalTokenService(
            JwtTokenProvider jwtTokenProvider,
            AuditLogService auditLogService,
            @Value("${robot-terminal.bootstrap-id:}") String bootstrapId,
            @Value("${robot-terminal.bootstrap-key:}") String bootstrapKey
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditLogService = auditLogService;
        this.bootstrapId = bootstrapId;
        this.bootstrapKey = bootstrapKey;
    }

    public DeviceTerminalBootstrapResponse bootstrap(DeviceTerminalBootstrapRequest request) {
        if (bootstrapId == null || bootstrapId.isBlank() || bootstrapKey == null || bootstrapKey.isBlank()) {
            throw new BusinessException(ErrorCode.AUTH_TERMINAL_BOOTSTRAP_DISABLED);
        }

        String requestedTerminalId = request.getTerminalId().trim();
        // TODO: replace FE-exposed shared bootstrap credential with per-device registry and secure provisioning.
        if (!bootstrapId.equals(requestedTerminalId) || !bootstrapKey.equals(request.getTerminalKey())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }

        List<String> scopes = List.of(
                DeviceTerminalScopes.CHECK_IN_CANDIDATES,
                DeviceTerminalScopes.CLAIM_MISSION
        );
        String deviceTerminalToken = jwtTokenProvider.createDeviceTerminalToken(requestedTerminalId, scopes);

        auditLogService.log(
                "DEVICE_TERMINAL_BOOTSTRAPPED",
                "TERMINAL",
                requestedTerminalId,
                "corr_terminal_" + requestedTerminalId,
                requestedTerminalId,
                "DEVICE_TERMINAL",
                Map.of(
                        "expiresInSeconds", jwtTokenProvider.getDeviceTerminalTokenExpiry(),
                        "scopes", scopes
                )
        );

        return DeviceTerminalBootstrapResponse.of(
                requestedTerminalId,
                deviceTerminalToken,
                jwtTokenProvider.getDeviceTerminalTokenExpiry(),
                scopes
        );
    }
}
