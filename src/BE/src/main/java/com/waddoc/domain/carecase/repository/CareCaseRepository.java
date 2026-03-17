package com.waddoc.domain.carecase.repository;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.carecase.entity.CareCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CareCaseRepository extends JpaRepository<CareCase, Long> {

    Optional<CareCase> findByPublicId(String publicId);

    Optional<CareCase> findByBooking(Booking booking);

    @EntityGraph(attributePaths = {"booking", "patient", "doctor", "doctor.user", "intakeSession"})
    Optional<CareCase> findWithDetailsByPublicId(String publicId);

    @Query("""
            select c
            from CareCase c
            join fetch c.booking b
            join fetch c.patient p
            join fetch c.doctor d
            join fetch d.user du
            left join fetch c.intakeSession i
            where d.user.publicId = :doctorUserPublicId
              and (:status is null or c.status = :status)
              and (:appointmentDate is null or b.appointmentDate = :appointmentDate)
            order by b.appointmentDate desc, b.startTime desc
            """)
    List<CareCase> findAllAssignedToDoctor(
            @Param("doctorUserPublicId") String doctorUserPublicId,
            @Param("status") CaseStatus status,
            @Param("appointmentDate") LocalDate appointmentDate
    );
}
