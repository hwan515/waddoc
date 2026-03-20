package com.waddoc.domain.guardian.controller;

import com.waddoc.domain.guardian.dto.GuardianPatientResponse;
import com.waddoc.domain.guardian.dto.GuardianPatientsResponse;
import com.waddoc.domain.guardian.service.GuardianQueryService;
import com.waddoc.global.error.GlobalExceptionHandler;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GuardianController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class GuardianControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GuardianQueryService guardianQueryService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void getLinkedPatientsReturnsPatientContactAndAddressFields() throws Exception {
        when(guardianQueryService.getLinkedPatients(isNull()))
                .thenReturn(GuardianPatientsResponse.of(List.of(
                        GuardianPatientResponse.builder()
                                .patientId("pat_Zk3mQ9")
                                .name("Kim Younghee")
                                .birthDate6("580315")
                                .phone("01012345678")
                                .regionCode("ULLEUNG")
                                .address("Ulleung-eup, Ulleung-gun")
                                .relation("DAUGHTER")
                                .approvedAt(LocalDate.of(2026, 1, 15))
                                .build()
                )));

        mockMvc.perform(get("/api/v1/guardians/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients[0].patientId").value("pat_Zk3mQ9"))
                .andExpect(jsonPath("$.patients[0].name").value("Kim Younghee"))
                .andExpect(jsonPath("$.patients[0].birthDate6").value("580315"))
                .andExpect(jsonPath("$.patients[0].phone").value("01012345678"))
                .andExpect(jsonPath("$.patients[0].regionCode").value("ULLEUNG"))
                .andExpect(jsonPath("$.patients[0].address").value("Ulleung-eup, Ulleung-gun"))
                .andExpect(jsonPath("$.patients[0].relation").value("DAUGHTER"))
                .andExpect(jsonPath("$.patients[0].approvedAt").value("2026-01-15"));
    }
}
