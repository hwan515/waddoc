package com.waddoc.domain.consultation.repository;

import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConsultationSummaryRepository extends JpaRepository<ConsultationSummary, Long> {

    Optional<ConsultationSummary> findBySession(ConsultationSession session);

    @Query("""
            select summary
            from ConsultationSummary summary
            join fetch summary.session session
            join fetch session.careCase careCase
            join fetch careCase.booking booking
            join fetch careCase.doctor doctor
            join fetch doctor.user doctorUser
            left join fetch careCase.intakeSession intakeSession
            where careCase.patient.publicId = :patientPublicId
              and session.status = :sessionStatus
            order by session.endedAt desc, booking.appointmentDate desc, booking.startTime desc
            """)
    List<ConsultationSummary> findAllByPatientPublicIdAndSessionStatus(
            @Param("patientPublicId") String patientPublicId,
            @Param("sessionStatus") ConsultationSessionStatus sessionStatus
    );
}
