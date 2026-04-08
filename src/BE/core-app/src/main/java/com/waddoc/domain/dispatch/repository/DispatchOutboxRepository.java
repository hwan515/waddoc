package com.waddoc.domain.dispatch.repository;

import com.waddoc.domain.dispatch.entity.DispatchOutbox;
import com.waddoc.domain.dispatch.entity.DispatchOutboxStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DispatchOutboxRepository extends JpaRepository<DispatchOutbox, Long> {

    @EntityGraph(attributePaths = {"careCase", "careCase.patient"})
    List<DispatchOutbox> findAllByStatusOrderByCreatedAtAsc(DispatchOutboxStatus status);

    @Query("""
            select o
            from DispatchOutbox o
            join fetch o.careCase c
            join fetch c.patient p
            where c.publicId = :casePublicId
            """)
    Optional<DispatchOutbox> findWithPatientByCareCasePublicId(@Param("casePublicId") String casePublicId);

    @Query("""
            select o
            from DispatchOutbox o
            join fetch o.careCase c
            join fetch c.patient p
            where o.regionCode = :regionCode
              and o.status = :status
            order by o.createdAt asc
            """)
    List<DispatchOutbox> findAllByRegionCodeAndStatusOrderByCreatedAtAsc(
            @Param("regionCode") String regionCode,
            @Param("status") DispatchOutboxStatus status
    );
}
