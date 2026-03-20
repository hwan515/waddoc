package com.waddoc.domain.mission.repository;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.mission.entity.Mission;
import com.waddoc.domain.mission.entity.MissionPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
            join fetch c.patient p
            where m.publicId = :publicId
            """)
    Optional<Mission> findWithDetailsByPublicId(@Param("publicId") String publicId);

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.patient p
            order by coalesce(m.dispatchedAt, m.createdAt) desc, m.publicId desc
            """)
    List<Mission> findAllForAdminDashboard();

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.patient p
            where m.phase = :phase
            order by coalesce(m.dispatchedAt, m.createdAt) desc, m.publicId desc
            """)
    List<Mission> findAllForAdminDashboardByPhase(@Param("phase") MissionPhase phase);

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.patient p
            where coalesce(m.dispatchedAt, m.createdAt) >= :fromDateTime
              and coalesce(m.dispatchedAt, m.createdAt) < :toDateTime
            order by coalesce(m.dispatchedAt, m.createdAt) desc, m.publicId desc
            """)
    List<Mission> findAllForAdminDashboardByDateRange(
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );

    @Query("""
            select m
            from Mission m
            join fetch m.careCase c
            join fetch c.patient p
            where m.phase = :phase
              and coalesce(m.dispatchedAt, m.createdAt) >= :fromDateTime
              and coalesce(m.dispatchedAt, m.createdAt) < :toDateTime
            order by coalesce(m.dispatchedAt, m.createdAt) desc, m.publicId desc
            """)
    List<Mission> findAllForAdminDashboardByPhaseAndDateRange(
            @Param("phase") MissionPhase phase,
            @Param("fromDateTime") LocalDateTime fromDateTime,
            @Param("toDateTime") LocalDateTime toDateTime
    );
}
