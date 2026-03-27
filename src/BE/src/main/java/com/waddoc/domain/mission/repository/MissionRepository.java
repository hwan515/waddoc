package com.waddoc.domain.mission.repository;

import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MissionRepository extends JpaRepository<Mission, Long> {

    Optional<Mission> findByPublicId(String publicId);

    Optional<Mission> findByCareCase(CareCase careCase);

    boolean existsByVehicleIdAndPhaseIn(String vehicleId, Collection<MissionPhase> phases);

    List<Mission> findAllByCareCaseIn(List<CareCase> careCases);

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.booking b
            join fetch c.patient p
            join fetch c.doctor d
            join fetch d.user du
            where p.birthDate6 = :birthDate6
              and p.phone like concat('%', :phoneLast4)
              and b.status = :bookingStatus
              and m.phase in :phases
            order by b.startTime asc, m.publicId asc
            """)
    List<Mission> findTerminalCandidates(
            @Param("phoneLast4") String phoneLast4,
            @Param("birthDate6") String birthDate6,
            @Param("bookingStatus") BookingStatus bookingStatus,
            @Param("phases") Collection<MissionPhase> phases
    );

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.patient p
            where m.publicId = :publicId
            """)
    Optional<Mission> findWithDetailsByPublicId(@Param("publicId") String publicId);

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.booking b
            join fetch c.patient p
            order by b.appointmentDate desc, b.startTime asc, m.publicId asc
            """)
    List<Mission> findAllForAdminDashboard();

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.booking b
            join fetch c.patient p
            where m.phase = :phase
            order by b.appointmentDate desc, b.startTime asc, m.publicId asc
            """)
    List<Mission> findAllForAdminDashboardByPhase(@Param("phase") MissionPhase phase);

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.booking b
            join fetch c.patient p
            where b.appointmentDate = :appointmentDate
            order by b.startTime asc, m.publicId asc
            """)
    List<Mission> findAllForAdminDashboardByAppointmentDate(
            @Param("appointmentDate") LocalDate appointmentDate
    );

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.booking b
            join fetch c.patient p
            where m.phase = :phase
              and b.appointmentDate = :appointmentDate
            order by b.startTime asc, m.publicId asc
            """)
    List<Mission> findAllForAdminDashboardByPhaseAndAppointmentDate(
            @Param("phase") MissionPhase phase,
            @Param("appointmentDate") LocalDate appointmentDate
    );

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.booking b
            join fetch c.patient p
            where m.vehicleId = :vehicleId
              and b.appointmentDate = :appointmentDate
              and b.status = :bookingStatus
              and m.phase in :phases
            order by b.startTime asc, m.publicId asc
            """)
    List<Mission> findCurrentVehicleMissions(
            @Param("vehicleId") String vehicleId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("bookingStatus") BookingStatus bookingStatus,
            @Param("phases") Collection<MissionPhase> phases
    );
}
