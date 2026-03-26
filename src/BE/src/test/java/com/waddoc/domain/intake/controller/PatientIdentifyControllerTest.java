package com.waddoc.domain.intake.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waddoc.domain.intake.dto.IdentifyByCallerNumberRequest;
import com.waddoc.domain.intake.dto.IdentifyByInfoRequest;
import com.waddoc.domain.intake.dto.IdentifyPatientResponse;
import com.waddoc.domain.intake.dto.IdentifyByPhoneRequest;
import com.waddoc.domain.intake.service.PatientIdentifyService;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
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

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientIdentifyController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PatientIdentifyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PatientIdentifyService patientIdentifyService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void identifyByCallerNumber_returnsPatientResponse() throws Exception {
        String intakeSessionId = "ints_test123";
        Patient patient = Patient.builder()
                .name("홍길동")
                .birthDate(LocalDate.of(1958, 3, 15))
                .regionCode("ULLEUNG")
                .address("울릉군")
                .build();

        when(patientIdentifyService.identifyByCallerNumber(eq(intakeSessionId), any(IdentifyByCallerNumberRequest.class)))
                .thenReturn(IdentifyPatientResponse.identified(patient));

        mockMvc.perform(post("/api/v1/intake/sessions/{intakeSessionId}/identify/by-caller-number", intakeSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IdentifyByCallerNumberRequest("01012345678"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identified").value(true))
                .andExpect(jsonPath("$.patient.patientId").value(patient.getPublicId()))
                .andExpect(jsonPath("$.patient.name").value("홍길동"))
                .andExpect(jsonPath("$.patient.birthDate6").value("580315"))
                .andExpect(jsonPath("$.patient.regionCode").value("ULLEUNG"));
    }

    @Test
    void identifyByPhone_returnsNotIdentifiedResponse() throws Exception {
        String intakeSessionId = "ints_test123";

        when(patientIdentifyService.identifyByPhone(eq(intakeSessionId), any(IdentifyByPhoneRequest.class)))
                .thenReturn(IdentifyPatientResponse.notIdentified());

        mockMvc.perform(post("/api/v1/intake/sessions/{intakeSessionId}/identify/by-phone", intakeSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IdentifyByPhoneRequest("01098765432"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identified").value(false))
                .andExpect(jsonPath("$.patient").value(nullValue()));
    }

    @Test
    void identifyByInfo_returnsBadRequest_whenBirthDateIsInvalid() throws Exception {
        String intakeSessionId = "ints_test123";

        mockMvc.perform(post("/api/v1/intake/sessions/{intakeSessionId}/identify/by-info", intakeSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IdentifyByInfoRequest("홍길동", "58031"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.details[0].field").value("birthDate6"));
    }

    @Test
    void identifyByInfo_returnsNotFound_whenSessionDoesNotExist() throws Exception {
        String intakeSessionId = "ints_missing";

        when(patientIdentifyService.identifyByInfo(eq(intakeSessionId), any(IdentifyByInfoRequest.class)))
                .thenThrow(new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        mockMvc.perform(post("/api/v1/intake/sessions/{intakeSessionId}/identify/by-info", intakeSessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new IdentifyByInfoRequest("홍길동", "580315"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("INTAKE_SESSION_NOT_FOUND"));
    }
}
