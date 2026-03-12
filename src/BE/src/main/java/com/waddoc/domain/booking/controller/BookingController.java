package com.waddoc.domain.booking.controller;

import com.waddoc.domain.booking.dto.*;
import com.waddoc.domain.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping("/intake/sessions/{sessionId}/existing-bookings")
    public ResponseEntity<BookingListResponse> getExistingBookings(
            @PathVariable String sessionId,
            @RequestParam(required = false) String status) {
        BookingListResponse response = bookingService.getExistingBookings(sessionId, status);
        return ResponseEntity.ok(response);
    }

    /** 4.3 — 예약 상세 조회 (인증 기반) */
    @GetMapping("/bookings/{bookingId}")
    public ResponseEntity<BookingDetailResponse> getBookingDetail(
            @PathVariable String bookingId) {
        // TODO: @AuthenticationPrincipal로 actorId/actorRole 추출 (JWT 필터 완성 시)
        String actorId = "SYSTEM";
        String actorRole = "SYSTEM";
        BookingDetailResponse response = bookingService.getBookingDetail(bookingId, actorId, actorRole);
        return ResponseEntity.ok(response);
    }

    /** 4.4 — 세션 기반 예약 취소 (무인증, 시뮬레이터) */
    @PostMapping("/intake/sessions/{sessionId}/existing-bookings/{bookingId}/cancel")
    public ResponseEntity<CancelBookingResponse> cancelBookingBySession(
            @PathVariable String sessionId,
            @PathVariable String bookingId,
            @RequestBody(required = false) CancelBookingRequest request) {
        CancelBookingResponse response = bookingService.cancelBookingBySession(sessionId, bookingId, request);
        return ResponseEntity.ok(response);
    }

    /** 4.5 — 인증 기반 예약 취소 */
    @PostMapping("/bookings/{bookingId}/cancel")
    public ResponseEntity<CancelBookingResponse> cancelBooking(
            @PathVariable String bookingId,
            @RequestBody(required = false) CancelBookingRequest request) {
        // TODO: @AuthenticationPrincipal로 actorId/actorRole 추출 (JWT 필터 완성 시)
        String actorId = "SYSTEM";
        String actorRole = "SYSTEM";
        CancelBookingResponse response = bookingService.cancelBooking(bookingId, request, actorId, actorRole);
        return ResponseEntity.ok(response);
    }
}
