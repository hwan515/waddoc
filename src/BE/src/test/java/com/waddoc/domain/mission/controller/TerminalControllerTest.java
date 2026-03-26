package com.waddoc.domain.mission.controller;

import com.waddoc.domain.mission.dto.DeviceTerminalBootstrapResponse;
import com.waddoc.domain.mission.dto.IssueMissionTerminalTokenResponse;
import com.waddoc.domain.mission.dto.TerminalCheckInCandidatesResponse;
import com.waddoc.domain.mission.service.DeviceTerminalTokenService;
import com.waddoc.domain.mission.service.TerminalCheckInService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TerminalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class TerminalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceTerminalTokenService deviceTerminalTokenService;

    @MockBean
    private TerminalCheckInService terminalCheckInService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void bootstrapToken_returnsDeviceTerminalToken() throws Exception {
        when(deviceTerminalTokenService.bootstrap(any()))
                .thenReturn(DeviceTerminalBootstrapResponse.builder()
                        .terminalId("robot-terminal-01")
                        .deviceTerminalToken("device-terminal-token")
                        .expiresIn(1800)
                        .scopes(List.of("terminal:check-in-candidates", "terminal:claim-mission"))
                        .build());

        mockMvc.perform(post("/api/v1/terminal/bootstrap-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "terminalId": "robot-terminal-01",
                                  "terminalKey": "bootstrap-key"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.terminalId").value("robot-terminal-01"))
                .andExpect(jsonPath("$.deviceTerminalToken").value("device-terminal-token"));
    }

    @Test
    void lookupCandidates_returnsMaskedCandidates() throws Exception {
        when(terminalCheckInService.lookupCandidates(any(), isNull()))
                .thenReturn(TerminalCheckInCandidatesResponse.of(List.of(
                        TerminalCheckInCandidatesResponse.Candidate.builder()
                                .missionId("ms_F2gHn6")
                                .patientMaskedName("홍*동")
                                .appointmentDate("2026-03-20")
                                .appointmentTime("14:30")
                                .doctorMaskedName("이*종")
                                .missionPhase("ARRIVED")
                                .build()
                )));

        mockMvc.perform(post("/api/v1/terminal/check-in/candidates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneLast4": "3720",
                                  "birthDate6": "580315"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.candidates[0].missionId").value("ms_F2gHn6"))
                .andExpect(jsonPath("$.candidates[0].patientMaskedName").value("홍*동"));
    }

    @Test
    void claimMission_returnsMissionTerminalToken() throws Exception {
        when(terminalCheckInService.claimMission(eq("ms_F2gHn6"), any(), isNull()))
                .thenReturn(IssueMissionTerminalTokenResponse.builder()
                        .missionId("ms_F2gHn6")
                        .caseId("case_T7nLp4")
                        .terminalToken("mission-terminal-token")
                        .expiresIn(1800)
                        .scopes(List.of("mission:identity-check", "session:issue-patient-token"))
                        .build());

        mockMvc.perform(post("/api/v1/terminal/missions/{missionId}/claim", "ms_F2gHn6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phoneLast4": "3720",
                                  "birthDate6": "580315"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("ms_F2gHn6"))
                .andExpect(jsonPath("$.terminalToken").value("mission-terminal-token"));
    }
}
