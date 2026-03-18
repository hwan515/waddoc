package com.waddoc.domain.patient.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CreatePatientRequest {

    @NotBlank(message = "name is required")
    private String name;

    @NotNull(message = "birthDate is required")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate birthDate;

    @NotBlank(message = "phone is required")
    private String phone;

    @NotBlank(message = "regionCode is required")
    private String regionCode;

    private String address;

    private MultipartFile referenceImage;
}
