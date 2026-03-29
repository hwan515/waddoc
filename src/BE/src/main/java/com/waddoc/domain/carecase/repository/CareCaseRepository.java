package com.waddoc.domain.carecase.repository;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CaseStatus;
import com.waddoc.domain.carecase.entity.CareCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    List<CareCase> findAllByBookingIn(List<Booking> bookings);

    @Query(
            value = """
                    select c
                    from CareCase c
                    join fetch c.booking b
                    join fetch c.patient p
                    join fetch c.doctor d
                    join fetch d.user du
                    left join fetch c.intakeSession i
                    where (b.channel is null or b.channel <> 'OUTPATIENT')
                    order by b.appointmentDate desc, b.startTime desc
                    """,
            countQuery = """
                    select count(c)
                    from CareCase c
                    where (c.booking.channel is null or c.booking.channel <> 'OUTPATIENT')
                    """
    )
    Page<CareCase> findAllForAdmin(Pageable pageable);

    @Query(
            value = """
                    select c
                    from CareCase c
                    join fetch c.booking b
                    join fetch c.patient p
                    join fetch c.doctor d
                    join fetch d.user du
                    left join fetch c.intakeSession i
                    where b.appointmentDate = :appointmentDate
                      and (b.channel is null or b.channel <> 'OUTPATIENT')
                    order by b.appointmentDate desc, b.startTime desc
                    """,
            countQuery = """
                    select count(c)
                    from CareCase c
                    join c.booking b
                    where b.appointmentDate = :appointmentDate
                      and (b.channel is null or b.channel <> 'OUTPATIENT')
                    """
    )
    Page<CareCase> findAllForAdminByAppointmentDate(
            @Param("appointmentDate") LocalDate appointmentDate,
            Pageable pageable
    );

    @Query(
            value = """
                    select c
                    from CareCase c
                    join fetch c.booking b
                    join fetch c.patient p
                    join fetch c.doctor d
                    join fetch d.user du
                    left join fetch c.intakeSession i
                    where c.status = :status
                      and (b.channel is null or b.channel <> 'OUTPATIENT')
                    order by b.appointmentDate desc, b.startTime desc
                    """,
            countQuery = """
                    select count(c)
                    from CareCase c
                    where c.status = :status
                      and (c.booking.channel is null or c.booking.channel <> 'OUTPATIENT')
                    """
    )
    Page<CareCase> findAllForAdminByStatus(
            @Param("status") CaseStatus status,
            Pageable pageable
    );

    @Query(
            value = """
                    select c
                    from CareCase c
                    join fetch c.booking b
                    join fetch c.patient p
                    join fetch c.doctor d
                    join fetch d.user du
                    left join fetch c.intakeSession i
                    where b.appointmentDate = :appointmentDate
                      and c.status = :status
                      and (b.channel is null or b.channel <> 'OUTPATIENT')
                    order by b.appointmentDate desc, b.startTime desc
                    """,
            countQuery = """
                    select count(c)
                    from CareCase c
                    join c.booking b
                    where b.appointmentDate = :appointmentDate
                      and c.status = :status
                      and (b.channel is null or b.channel <> 'OUTPATIENT')
                    """
    )
    Page<CareCase> findAllForAdminByAppointmentDateAndStatus(
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("status") CaseStatus status,
            Pageable pageable
    );
}
