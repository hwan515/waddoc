package com.waddoc.domain.patient.entity;

import com.waddoc.domain.user.entity.User;
import com.waddoc.global.audit.BaseTimeEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 환자 정보. 계정 없이 관리자가 사전 등록. birthDate6은 조회용 6자리(YYMMDD).
 */
@Entity
@Table(name = "patient")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "patient_id")
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 20)
    private String publicId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "birth_date6", nullable = false, columnDefinition = "bpchar(6)")
    private String birthDate6;

    @Column(name = "region_code", length = 30)
    private String regionCode;

    @Column(length = 255)
    private String address;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "reference_image_path", length = 500)
    private String referenceImagePath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reference_image_uploaded_by_user_id")
    private User referenceImageUploadedBy;

    @Column(name = "reference_image_updated_at")
    private LocalDateTime referenceImageUpdatedAt;

    @Builder
    public Patient(String name, LocalDate birthDate, String regionCode, String address, String phone) {
        this.publicId = PublicIdGenerator.generate("pat_");
        this.name = name;
        this.birthDate = birthDate;
        this.birthDate6 = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        this.regionCode = regionCode;
        this.address = address;
        this.phone = phone;
    }

    public void updateReferenceImage(String referenceImagePath, User uploadedBy) {
        this.referenceImagePath = referenceImagePath;
        this.referenceImageUploadedBy = uploadedBy;
        this.referenceImageUpdatedAt = LocalDateTime.now();
    }

    public boolean hasReferenceImage() {
        return this.referenceImagePath != null && !this.referenceImagePath.isBlank();
    }
}
