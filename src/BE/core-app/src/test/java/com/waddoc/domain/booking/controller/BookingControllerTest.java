package com.waddoc.domain.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.booking.dto.BookingListResponse;
import com.waddoc.domain.booking.dto.BookingSummaryResponse;
import com.waddoc.domain.booking.dto.CancelBookingResponse;
import com.waddoc.domain.booking.service.BookingService;
import com.waddoc.global.error.GlobalExceptionHandler;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getExistingBookings_usesDocumentedBookingsPath() throws Exception {
        String sessionId = "ints_test123";

        when(bookingService.getExistingBookings(eq(sessionId), isNull()))
                .thenReturn(BookingListResponse.of(List.of(
                        BookingSummaryResponse.builder()
                                .bookingId("bk_test123")
                                .status("CONFIRMED")
                                .doctorName("김의사")
                                .departmentName("내과")
                                .appointmentDate(LocalDate.of(2026, 3, 17))
                                .startTime(LocalTime.of(10, 0))
                                .endTime(LocalTime.of(10, 30))
                                .createdAt(OffsetDateTime.parse("2026-03-17T09:00:00+09:00"))
                                .build()
                )));

        mockMvc.perform(get("/api/v1/intake/sessions/{sessionId}/bookings", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings[0].bookingId").value("bk_test123"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void cancelBookingBySession_usesDocumentedBookingsCancelPath() throws Exception {
        String sessionId = "ints_test123";
        String bookingId = "bk_test123";

        when(bookingService.cancelBookingBySession(eq(sessionId), eq(bookingId), isNull()))
                .thenReturn(CancelBookingResponse.builder()
                        .bookingId(bookingId)
                        .status("CANCELLED")
                        .cancelledAt(OffsetDateTime.parse("2026-03-17T09:10:00+09:00"))
                        .ttsMessage("예약이 취소되었습니다.")
                        .build());

        mockMvc.perform(post("/api/v1/intake/sessions/{sessionId}/bookings/{bookingId}/cancel", sessionId, bookingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(bookingId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
