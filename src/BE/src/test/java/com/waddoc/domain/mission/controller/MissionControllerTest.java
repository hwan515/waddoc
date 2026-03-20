package com.waddoc.domain.mission.controller;

import com.waddoc.domain.mission.dto.CreateMissionResponse;
import com.waddoc.domain.mission.dto.MissionDetailResponse;
import com.waddoc.domain.mission.dto.MissionIdentityCheckResponse;
import com.waddoc.domain.mission.dto.MissionListResponse;
import com.waddoc.domain.mission.dto.MissionSummaryResponse;
import com.waddoc.domain.mission.dto.UpdateMissionPhaseResponse;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.domain.mission.service.MissionCommandService;
import com.waddoc.domain.mission.service.MissionIdentityCheckService;
import com.waddoc.domain.mission.service.MissionQueryService;
import com.waddoc.global.error.GlobalExceptionHandler;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MissionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MissionQueryService missionQueryService;

    @MockBean
    private MissionCommandService missionCommandService;

    @MockBean
    private MissionIdentityCheckService missionIdentityCheckService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getMissionsReturnsMissionDashboardList() throws Exception {
        when(missionQueryService.getMissions(
                isNull(),
                eq(LocalDate.of(2026, 3, 11)),
                eq(MissionPhase.DISPATCHED)
        )).thenReturn(MissionListResponse.of(List.of(
                MissionSummaryResponse.builder()
                        .missionId("ms_F2gHn6")
                        .caseId("case_T7nLp4")
                        .patientName("홍길동")
                        .phase(MissionPhase.DISPATCHED)
                        .vehicleId("v-001")
                        .destination("경북 울릉군 울릉읍...")
                        .dispatchedAt(OffsetDateTime.parse("2026-03-11T08:30:00+09:00"))
                        .estimatedArrivalTime(OffsetDateTime.parse("2026-03-11T09:45:00+09:00"))
                        .build()
        )));

        mockMvc.perform(get("/api/v1/missions")
                        .param("date", "2026-03-11")
                        .param("phase", "DISPATCHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missions[0].missionId").value("ms_F2gHn6"))
                .andExpect(jsonPath("$.missions[0].caseId").value("case_T7nLp4"))
                .andExpect(jsonPath("$.missions[0].phase").value("DISPATCHED"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void createMissionReturnsCreatedResponse() throws Exception {
        when(missionCommandService.createMission(
                isNull(),
                any()
        )).thenReturn(CreateMissionResponse.builder()
                .missionId("ms_F2gHn6")
                .caseId("case_T7nLp4")
                .phase(MissionPhase.CREATED)
                .vehicleId("v-001")
                .createdAt(OffsetDateTime.parse("2026-03-10T14:00:00+09:00"))
                .build());

        mockMvc.perform(post("/api/v1/missions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case_T7nLp4",
                                  "vehicleId": "v-001",
                                  "destination": "Ulleung",
                                  "scheduledTime": "2026-03-11T08:30:00+09:00"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.missionId").value("ms_F2gHn6"))
                .andExpect(jsonPath("$.caseId").value("case_T7nLp4"))
                .andExpect(jsonPath("$.phase").value("CREATED"))
                .andExpect(jsonPath("$.vehicleId").value("v-001"))
                .andExpect(jsonPath("$.createdAt").value("2026-03-10T14:00:00+09:00"));
    }

    @Test
    void updateMissionPhaseReturnsUpdatedResponse() throws Exception {
        when(missionCommandService.updateMissionPhase(
                isNull(),
                eq("ms_F2gHn6"),
                any()
        )).thenReturn(UpdateMissionPhaseResponse.builder()
                .missionId("ms_F2gHn6")
                .phase(MissionPhase.ARRIVED)
                .previousPhase(MissionPhase.EN_ROUTE)
                .updatedAt(OffsetDateTime.parse("2026-03-11T09:45:00+09:00"))
                .build());

        mockMvc.perform(patch("/api/v1/missions/{missionId}", "ms_F2gHn6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phase": "ARRIVED",
                                  "reason": "Arrived on site"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("ms_F2gHn6"))
                .andExpect(jsonPath("$.phase").value("ARRIVED"))
                .andExpect(jsonPath("$.previousPhase").value("EN_ROUTE"))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-11T09:45:00+09:00"));
    }

    @Test
    void getMissionDetailReturnsMissionDetail() throws Exception {
        when(missionQueryService.getMissionDetail(
                isNull(),
                eq("ms_F2gHn6")
        )).thenReturn(MissionDetailResponse.builder()
                .missionId("ms_F2gHn6")
                .caseId("case_T7nLp4")
                .phase(MissionPhase.EN_ROUTE)
                .vehicleId("v-001")
                .patientName("Hong Gil-dong")
                .destination("Ulleung")
                .dispatchedAt(OffsetDateTime.parse("2026-03-11T08:30:00+09:00"))
                .estimatedArrivalTime(OffsetDateTime.parse("2026-03-11T09:45:00+09:00"))
                .currentLocation(MissionDetailResponse.CurrentLocation.builder()
                        .latitude(new BigDecimal("37.4845"))
                        .longitude(new BigDecimal("130.9057"))
                        .timestamp(OffsetDateTime.parse("2026-03-11T09:15:00+09:00"))
                        .build())
                .updatedAt(OffsetDateTime.parse("2026-03-11T09:45:00+09:00"))
                .build());

        mockMvc.perform(get("/api/v1/missions/{missionId}", "ms_F2gHn6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("ms_F2gHn6"))
                .andExpect(jsonPath("$.caseId").value("case_T7nLp4"))
                .andExpect(jsonPath("$.phase").value("EN_ROUTE"))
                .andExpect(jsonPath("$.currentLocation.latitude").value(37.4845))
                .andExpect(jsonPath("$.currentLocation.longitude").value(130.9057))
                .andExpect(jsonPath("$.updatedAt").value("2026-03-11T09:45:00+09:00"));
    }

    @Test
    void verifyMissionIdentity_usesDocumentedMultipartPath() throws Exception {
        when(missionIdentityCheckService.verify(
                eq("ms_F2gHn6"),
                eq("pat_T7nLp4"),
                any(),
                any(),
                isNull()
        )).thenReturn(MissionIdentityCheckResponse.builder()
                .missionId("ms_F2gHn6")
                .patientId("pat_T7nLp4")
                .status("VERIFIED")
                .verifiedAt(OffsetDateTime.parse("2026-03-11T09:58:00+09:00"))
                .expiresInSeconds(600)
                .identityCheck(MissionIdentityCheckResponse.IdentityCheckDetail.builder()
                        .matched(true)
                        .faceSimilarityScore(0.94)
                        .idCardFaceSimilarityScore(0.91)
                        .reasonCodes(List.of())
                        .ocr(MissionIdentityCheckResponse.OcrDetail.builder()
                                .name("홍길동")
                                .rrnMasked("580315-1******")
                                .address("경북 울릉군 울릉읍...")
                                .build())
                        .build())
                .nextStep("VITALS")
                .build());

        MockMultipartFile patientId = new MockMultipartFile("patientId", "", MediaType.TEXT_PLAIN_VALUE, "pat_T7nLp4".getBytes());
        MockMultipartFile faceImage = new MockMultipartFile("faceImage", "face.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3});
        MockMultipartFile idCardImage = new MockMultipartFile("idCardImage", "id-card.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{4, 5, 6});

        mockMvc.perform(multipart("/api/v1/missions/{missionId}/identity-check", "ms_F2gHn6")
                        .file(patientId)
                        .file(faceImage)
                        .file(idCardImage))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("ms_F2gHn6"))
                .andExpect(jsonPath("$.patientId").value("pat_T7nLp4"))
                .andExpect(jsonPath("$.status").value("VERIFIED"))
                .andExpect(jsonPath("$.nextStep").value("VITALS"));
    }
}
