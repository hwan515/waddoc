package com.waddoc.domain.patient.repository;

import com.waddoc.domain.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPublicId(String publicId);

    Optional<Patient> findByPhone(String phone);

    List<Patient> findAllByReferenceImagePathOrderByIdAsc(String referenceImagePath);

    List<Patient> findAllByNameAndBirthDate6(String name, String birthDate6);

    Page<Patient> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Patient> findAllByNameContainingIgnoreCaseOrderByCreatedAtDesc(String name, Pageable pageable);

    Page<Patient> findAllByPhoneContainingOrderByCreatedAtDesc(String phone, Pageable pageable);

    Page<Patient> findAllByNameContainingIgnoreCaseAndPhoneContainingOrderByCreatedAtDesc(
            String name,
            String phone,
            Pageable pageable
    );
}
