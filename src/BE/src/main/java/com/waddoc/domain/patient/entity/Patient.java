package com.waddoc.domain.patient.entity;

import com.waddoc.global.audit.BaseTimeEntity;
import com.waddoc.global.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    @Builder
    public Patient(String name, LocalDate birthDate, String regionCode, String address) {
        this.publicId = PublicIdGenerator.generate("pat_");
        this.name = name;
        this.birthDate = birthDate;
        this.birthDate6 = birthDate.format(DateTimeFormatter.ofPattern("yyMMdd"));
        this.regionCode = regionCode;
        this.address = address;
    }
}
