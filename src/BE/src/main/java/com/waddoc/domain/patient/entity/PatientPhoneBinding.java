package com.waddoc.domain.patient.entity;

import com.waddoc.global.audit.BaseCreatedEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 환자-전화번호 바인딩. 전역 unique (1번호=1환자 원칙).
 */
@Entity
@Table(name = "patient_phone_binding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatientPhoneBinding extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "binding_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Builder
    public PatientPhoneBinding(Patient patient, String phone, boolean primary) {
        this.patient = patient;
        this.phone = phone;
        this.primary = primary;
    }
}
