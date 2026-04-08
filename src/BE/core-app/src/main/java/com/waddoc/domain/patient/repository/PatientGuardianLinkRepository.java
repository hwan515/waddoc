package com.waddoc.domain.patient.repository;

import com.waddoc.domain.patient.entity.GuardianLinkStatus;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.domain.patient.entity.PatientGuardianLink;
import com.waddoc.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientGuardianLinkRepository extends JpaRepository<PatientGuardianLink, Long> {

    Optional<PatientGuardianLink> findByPublicId(String publicId);

    @EntityGraph(attributePaths = {"patient", "guardianUser", "approvedByUser"})
    Optional<PatientGuardianLink> findDetailedByPublicId(String publicId);

    Optional<PatientGuardianLink> findByPatientAndGuardianUser(Patient patient, User guardianUser);

    boolean existsByPatientAndGuardianUser(Patient patient, User guardianUser);

    boolean existsByGuardianUserIdAndStatus(Long guardianUserId, GuardianLinkStatus status);

    boolean existsByPatientPublicIdAndGuardianUserPublicIdAndStatus(
            String patientPublicId,
            String guardianUserPublicId,
            GuardianLinkStatus status
    );

    @EntityGraph(attributePaths = {"patient"})
    List<PatientGuardianLink> findAllByGuardianUserPublicIdAndStatus(
            String guardianUserPublicId,
            GuardianLinkStatus status
    );

    @Query(
            value = """
                    select link
                    from PatientGuardianLink link
                    join fetch link.patient patient
                    join fetch link.guardianUser guardianUser
                    where (:status is null or link.status = :status)
                    order by link.requestedAt desc
                    """,
            countQuery = """
                    select count(link)
                    from PatientGuardianLink link
                    where (:status is null or link.status = :status)
                    """
    )
    Page<PatientGuardianLink> searchAdminGuardianLinkRequests(
            @Param("status") GuardianLinkStatus status,
            Pageable pageable
    );
}
