package com.waddoc.domain.patient.repository;

import com.waddoc.domain.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    Optional<Patient> findByPublicId(String publicId);

    List<Patient> findAllByNameAndBirthDate6(String name, String birthDate6);
}
