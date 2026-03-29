package com.waddoc.domain.booking.controller;

import com.waddoc.domain.booking.dto.*;
import com.waddoc.domain.booking.service.BookingService;
import com.waddoc.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 접수 세션에서 이어지는 예약 생성, 조회, 취소 API를 제공한다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /** 4.1 — 예약 생성 (무인증, 세션 기반) */
    @PostMapping("/intake/sessions/{sessionId}/bookings")
    public ResponseEntity<CreateBookingResponse> createBooking(
            @PathVariable String sessionId,
            @Valid @RequestBody CreateBookingRequest request) {
        CreateBookingResponse response = bookingService.createBooking(sessionId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** 4.2 — 기존 예약 조회 (무인증, 세션 기반) */
    @GetMapping("/intake/sessions/{sessionId}/bookings")
    public ResponseEntity<BookingListResponse> getExistingBookings(
            @PathVariable String sessionId,
            @RequestParam(required = false) String status) {
        BookingListResponse response = bookingService.getExistingBookings(sessionId, status);
        return ResponseEntity.ok(response);
    }

    /** 4.3 — 예약 상세 조회 (인증 기반) */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<BookingDetailResponse> getBookingDetail(
            @PathVariable String bookingId,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        BookingDetailResponse response = bookingService.getBookingDetail(
                bookingId,
                authenticatedUser.userId(),
                authenticatedUser.role().name()
        );
        return ResponseEntity.ok(response);
    }

    /** 4.4 — 세션 기반 예약 취소 (무인증, 시뮬레이터) */
    @PostMapping("/intake/sessions/{sessionId}/bookings/{bookingId}/cancel")
    public ResponseEntity<CancelBookingResponse> cancelBookingBySession(
            @PathVariable String sessionId,
            @PathVariable String bookingId,
            @RequestBody(required = false) CancelBookingRequest request) {
        CancelBookingResponse response = bookingService.cancelBookingBySession(sessionId, bookingId, request);
        return ResponseEntity.ok(response);
    }

    /** 4.5 — 인증 기반 예약 취소 */
    @PreAuthorize("hasAnyRole('ADMIN', 'DOCTOR')")
    @PostMapping("/bookings/{bookingId}/cancel")
    public ResponseEntity<CancelBookingResponse> cancelBooking(
            @PathVariable String bookingId,
            @RequestBody(required = false) CancelBookingRequest request,
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser) {
        CancelBookingResponse response = bookingService.cancelBooking(
                bookingId,
                request,
                authenticatedUser.userId(),
                authenticatedUser.role().name()
        );
        return ResponseEntity.ok(response);
    }
}
