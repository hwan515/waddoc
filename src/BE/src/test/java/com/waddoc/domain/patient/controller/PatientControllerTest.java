package com.waddoc.domain.patient.controller;

import com.waddoc.domain.patient.dto.CreatePatientResponse;
import com.waddoc.domain.patient.entity.PatientGender;
import com.waddoc.domain.patient.service.PatientCommandService;
import com.waddoc.global.error.GlobalExceptionHandler;
import com.waddoc.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PatientController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PatientControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientCommandService patientCommandService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void createPatientReturnsCreatedResponse() throws Exception {
        MockMultipartFile referenceImage = new MockMultipartFile(
                "referenceImage",
                "face.jpg",
                "image/jpeg",
                "image-bytes".getBytes()
        );

        when(patientCommandService.createPatient(isNull(), any()))
                .thenReturn(CreatePatientResponse.builder()
                        .patientId("pat_R7xNw3")
                        .name("Hong Gil-dong")
                        .birthDate6("580315")
                        .phone("01012345678")
                        .gender(PatientGender.MALE)
                        .referenceImageRegistered(true)
                        .build());

        mockMvc.perform(multipart("/api/v1/patients")
                        .file(referenceImage)
                        .param("name", "Hong Gil-dong")
                        .param("birthDate", "1958-03-15")
                        .param("phone", "01012345678")
                        .param("gender", "MALE")
                        .param("regionCode", "ULLEUNG")
                        .param("address", "Ulleung-eup"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/patients/pat_R7xNw3"))
                .andExpect(jsonPath("$.patientId").value("pat_R7xNw3"))
                .andExpect(jsonPath("$.name").value("Hong Gil-dong"))
                .andExpect(jsonPath("$.birthDate6").value("580315"))
                .andExpect(jsonPath("$.phone").value("01012345678"))
                .andExpect(jsonPath("$.gender").value("MALE"))
                .andExpect(jsonPath("$.referenceImageRegistered").value(true));
    }

    @Test
    void createPatientWithoutNameReturnsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/v1/patients")
                        .param("birthDate", "1958-03-15")
                        .param("phone", "01012345678")
                        .param("gender", "MALE")
                        .param("regionCode", "ULLEUNG"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_INPUT"));
    }
}
