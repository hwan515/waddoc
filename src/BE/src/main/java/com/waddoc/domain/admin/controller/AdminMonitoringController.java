package com.waddoc.domain.admin.controller;

import com.waddoc.domain.admin.service.AdminMonitoringService;
import com.waddoc.global.security.AuthenticatedUser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/monitoring")
@RequiredArgsConstructor
public class AdminMonitoringController {

    private static final String MONITORING_COOKIE_NAME = "monitoring_access";

    private final AdminMonitoringService adminMonitoringService;

    @PostMapping("/session")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> issueSession(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            HttpServletResponse response
    ) {
        adminMonitoringService.issueSession(authenticatedUser, response);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/session")
    public ResponseEntity<Void> revokeSession(HttpServletResponse response) {
        adminMonitoringService.revokeSession(response);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/authorize")
    public ResponseEntity<Void> authorize(
            @CookieValue(name = MONITORING_COOKIE_NAME, required = false) String monitoringToken
    ) {
        adminMonitoringService.authorize(monitoringToken);
        return ResponseEntity.noContent().build();
    }
}
