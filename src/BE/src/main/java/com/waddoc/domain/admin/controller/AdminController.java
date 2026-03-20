package com.waddoc.domain.admin.controller;

import com.waddoc.domain.admin.dto.*;
import com.waddoc.domain.admin.service.AdminService;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/bookings")
    public ResponseEntity<AdminBookingListResponse> getBookings(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminService.getBookings(authenticatedUser, date, status, page, size));
    }

    @GetMapping("/cases")
    public ResponseEntity<AdminCaseListResponse> getCases(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) CaseStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminService.getCases(authenticatedUser, date, status, page, size));
    }

    @GetMapping("/sessions")
    public ResponseEntity<AdminSessionListResponse> getSessions(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ConsultationSessionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminService.getSessions(authenticatedUser, date, status, page, size));
    }

    @GetMapping("/patients")
    public ResponseEntity<AdminPatientListResponse> getPatients(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String phone,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminService.getPatients(authenticatedUser, name, phone, page, size));
    }

    @GetMapping("/guardian-link-requests")
    public ResponseEntity<GuardianLinkRequestListResponse> getGuardianLinkRequests(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @RequestParam(required = false) GuardianLinkStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminService.getGuardianLinkRequests(authenticatedUser, status, page, size));
    }

    @PostMapping("/guardian-link-requests/{linkId}/approve")
    public ResponseEntity<GuardianLinkApprovalResponse> approveGuardianLinkRequest(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String linkId,
            @RequestBody(required = false) ApproveGuardianLinkRequest request
    ) {
        return ResponseEntity.ok(adminService.approveGuardianLinkRequest(authenticatedUser, linkId));
    }

    @PostMapping("/guardian-link-requests/{linkId}/reject")
    public ResponseEntity<GuardianLinkRejectionResponse> rejectGuardianLinkRequest(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable String linkId,
            @RequestBody(required = false) RejectGuardianLinkRequest request
    ) {
        return ResponseEntity.ok(adminService.rejectGuardianLinkRequest(authenticatedUser, linkId));
    }
}
