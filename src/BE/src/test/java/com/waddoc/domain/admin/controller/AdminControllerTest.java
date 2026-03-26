package com.waddoc.domain.admin.controller;

import com.waddoc.domain.admin.dto.AdminDemoMissionActionResponse;
import com.waddoc.domain.admin.service.AdminDemoMissionService;
import com.waddoc.domain.admin.service.AdminService;
import com.waddoc.domain.mission.entity.MissionPhase;
import com.waddoc.global.error.GlobalExceptionHandler;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @MockBean
    private AdminDemoMissionService adminDemoMissionService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void dispatchDemoMission_returnsActionResponse() throws Exception {
        when(adminDemoMissionService.dispatchMission(isNull(), eq("ms_demo_01")))
                .thenReturn(AdminDemoMissionActionResponse.builder()
                        .missionId("ms_demo_01")
                        .phase(MissionPhase.EN_ROUTE)
                        .previousPhase(MissionPhase.CREATED)
                        .vehicleId("veh_GIMCHEON_01")
                        .targetWaypointNumber(59)
                        .waypointCommandSent(true)
                        .dummyCompleted(false)
                        .build());

        mockMvc.perform(post("/api/v1/admin/demo/missions/{missionId}/dispatch", "ms_demo_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.missionId").value("ms_demo_01"))
                .andExpect(jsonPath("$.phase").value("EN_ROUTE"))
                .andExpect(jsonPath("$.previousPhase").value("CREATED"))
                .andExpect(jsonPath("$.vehicleId").value("veh_GIMCHEON_01"))
                .andExpect(jsonPath("$.targetWaypointNumber").value(59))
                .andExpect(jsonPath("$.waypointCommandSent").value(true))
                .andExpect(jsonPath("$.dummyCompleted").value(false));
    }
}
