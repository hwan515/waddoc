package com.waddoc.domain.patient.repository;

import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientPhoneBinding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientPhoneBindingRepository extends JpaRepository<PatientPhoneBinding, Long> {

    Optional<PatientPhoneBinding> findFirstByPatientAndPrimaryTrue(Patient patient);

    Optional<PatientPhoneBinding> findByPhone(String phone);
}
