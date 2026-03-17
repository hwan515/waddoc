package com.waddoc.domain.patient.repository;

import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PatientGuardianLinkRepository extends JpaRepository<PatientGuardianLink, Long> {

    Optional<PatientGuardianLink> findByPublicId(String publicId);

    Optional<PatientGuardianLink> findByPatientAndGuardianUser(Patient patient, User guardianUser);

    boolean existsByPatientAndGuardianUser(Patient patient, User guardianUser);
}
