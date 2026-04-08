package com.waddoc.domain.booking.repository;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.patient.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPublicId(String publicId);

    Optional<Booking> findBySlot(ScheduleSlot slot);

    @Query("""
            select b
            from Booking b
            where b.regionCode = :regionCode
              and b.appointmentDate >= :fromDate
              and b.status <> :excludedStatus
            order by b.appointmentDate asc, b.startTime asc
            """)
    List<Booking> findActiveRegionBookingsFromDate(
            @Param("regionCode") String regionCode,
            @Param("fromDate") LocalDate fromDate,
            @Param("excludedStatus") BookingStatus excludedStatus
    );

    @Query("""
            select (count(b) > 0)
            from Booking b
            where b.regionCode = :regionCode
              and b.appointmentDate = :appointmentDate
              and b.status <> :excludedStatus
              and b.startTime < :endTime
              and b.endTime > :startTime
            """)
    boolean existsActiveRegionBookingConflict(
            @Param("regionCode") String regionCode,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("excludedStatus") BookingStatus excludedStatus
    );

    @Query("""
            select b
            from Booking b
            where b.patient = :patient
              and b.doctor.department = :department
              and b.status <> :excludedStatus
              and (
                    b.appointmentDate < :today
                    or (b.appointmentDate = :today and b.startTime < :currentTime)
                  )
            order by b.appointmentDate desc, b.startTime desc
            """)
    List<Booking> findRecentPastDepartmentBookings(
            @Param("patient") Patient patient,
            @Param("department") String department,
            @Param("excludedStatus") BookingStatus excludedStatus,
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime,
            Pageable pageable
    );

    List<Booking> findByPatient(Patient patient);

    List<Booking> findByPatientAndStatus(Patient patient, BookingStatus status);

    @Query("""
            select b
            from Booking b
            where b.patient = :patient
              and b.status = :status
              and (
                    b.appointmentDate > :today
                    or (b.appointmentDate = :today and b.startTime > :currentTime)
                  )
            order by b.appointmentDate asc, b.startTime asc
            """)
    List<Booking> findUpcomingBookingsByPatientAndStatus(
            @Param("patient") Patient patient,
            @Param("status") BookingStatus status,
            @Param("today") LocalDate today,
            @Param("currentTime") LocalTime currentTime
    );

    @Query(
            value = """
                    select b
                    from Booking b
                    join fetch b.patient p
                    join fetch b.doctor d
                    join fetch d.user du
                    where (:appointmentDate is null or b.appointmentDate = :appointmentDate)
                      and (:status is null or b.status = :status)
                      and (b.channel is null or b.channel <> 'OUTPATIENT')
                    order by b.appointmentDate desc, b.startTime desc
                    """,
            countQuery = """
                    select count(b)
                    from Booking b
                    where (:appointmentDate is null or b.appointmentDate = :appointmentDate)
                      and (:status is null or b.status = :status)
                      and (b.channel is null or b.channel <> 'OUTPATIENT')
                    """
    )
    Page<Booking> searchAdminBookings(
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("status") BookingStatus status,
            Pageable pageable
    );
}
