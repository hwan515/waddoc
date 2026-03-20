package com.waddoc.domain.patient.service;

import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class PatientReferenceImageStorageService {

    private final Path storageRoot;

    public PatientReferenceImageStorageService(@Value("${file.storage-root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    public String save(String patientPublicId, MultipartFile referenceImage) {
        String extension = resolveExtension(referenceImage);
        Path patientDirectory = storageRoot.resolve("patients").resolve(patientPublicId);
        String filename = "reference." + extension;
        Path targetPath = patientDirectory.resolve(filename);

        try {
            Files.createDirectories(patientDirectory);
            try (InputStream inputStream = referenceImage.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_SAVE_FAILED);
        }

        return "patients/" + patientPublicId + "/" + filename;
    }

    private String resolveExtension(MultipartFile referenceImage) {
        String contentType = referenceImage.getContentType();
        if (MediaType.IMAGE_JPEG_VALUE.equalsIgnoreCase(contentType) || "image/jpg".equalsIgnoreCase(contentType)) {
            return "jpg";
        }
        if (MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(contentType)) {
            return "png";
        }
        throw new BusinessException(ErrorCode.PATIENT_REFERENCE_IMAGE_INVALID);
    }
}
