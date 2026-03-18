package com.waddoc.domain.mission.controller;

import com.waddoc.domain.mission.service.MissionTelemetryService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MissionTelemetryController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class MissionTelemetryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MissionTelemetryService missionTelemetryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void receiveTelemetryReturnsAccepted() throws Exception {
        doNothing().when(missionTelemetryService).receiveTelemetry(
                eq("ms_F2gHn6"),
                eq("telemetry-dev-key"),
                any()
        );

        mockMvc.perform(post("/api/v1/missions/{missionId}/telemetry", "ms_F2gHn6")
                        .header("X-API-Key", "telemetry-dev-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "ROS2",
                                  "sourceEventId": "ros2_msg_abc123",
                                  "seqNo": 42,
                                  "vehicleId": "v-001",
                                  "phase": "EN_ROUTE",
                                  "latitude": 37.4845,
                                  "longitude": 130.9057,
                                  "speed": 30.5,
                                  "heading": 180,
                                  "timestamp": "2026-03-11T09:15:00+09:00",
                                  "metadata": {}
                                }
                                """))
                .andExpect(status().isAccepted());
    }

    @Test
    void receiveTelemetryRequiresValidBody() throws Exception {
        mockMvc.perform(post("/api/v1/missions/{missionId}/telemetry", "ms_F2gHn6")
                        .header("X-API-Key", "telemetry-dev-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source": "ROS2"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
