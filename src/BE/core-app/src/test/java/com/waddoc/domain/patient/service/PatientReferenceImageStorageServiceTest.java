package com.waddoc.domain.patient.service;

import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatientReferenceImageStorageServiceTest {

    @TempDir
    Path tempDirectory;

    @Test
    void saveStoresReferenceImageUnderPatientDirectory() throws Exception {
        PatientReferenceImageStorageService storageService =
                new PatientReferenceImageStorageService(tempDirectory.toString());
        MockMultipartFile referenceImage = new MockMultipartFile(
                "referenceImage",
                "face.png",
                "image/png",
                "image-bytes".getBytes()
        );

        String savedPath = storageService.save("pat_R7xNw3", referenceImage);

        assertThat(savedPath).isEqualTo("patients/pat_R7xNw3/reference.png");
        assertThat(Files.exists(tempDirectory.resolve("patients").resolve("pat_R7xNw3").resolve("reference.png")))
                .isTrue();
    }

    @Test
    void saveRejectsUnsupportedImageType() {
        PatientReferenceImageStorageService storageService =
                new PatientReferenceImageStorageService(tempDirectory.toString());
        MockMultipartFile referenceImage = new MockMultipartFile(
                "referenceImage",
                "face.gif",
                "image/gif",
                "image-bytes".getBytes()
        );

        assertThatThrownBy(() -> storageService.save("pat_R7xNw3", referenceImage))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PATIENT_REFERENCE_IMAGE_INVALID);
    }
}
