package com.waddoc.domain.consultation.repository;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSessionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ConsultationSessionRepository extends JpaRepository<ConsultationSession, Long> {

    Optional<ConsultationSession> findByPublicId(String publicId);

    // 요약 저장 시 권한 체크와 응답 조립에 필요한 연관 엔티티를 한 번에 조회한다.
    @Query("""
            select s
            from ConsultationSession s
            join fetch s.careCase c
            join fetch c.booking b
            join fetch c.doctor d
            join fetch d.user du
            where s.publicId = :publicId
            """)
    Optional<ConsultationSession> findWithDoctorAndCaseByPublicId(@Param("publicId") String publicId);

    @Query("""
            select s
            from ConsultationSession s
            join fetch s.careCase c
            join fetch c.patient p
            join fetch c.doctor d
            join fetch d.user du
            where s.roomId = :roomId
            """)
    Optional<ConsultationSession> findWithParticipantsByRoomId(@Param("roomId") String roomId);

    Optional<ConsultationSession> findByCareCase(CareCase careCase);

    List<ConsultationSession> findAllByCareCaseIn(List<CareCase> careCases);

    @Query(
            value = """
                    select s
                    from ConsultationSession s
                    join fetch s.careCase c
                    join fetch c.booking b
                    join fetch c.patient p
                    join fetch c.doctor d
                    join fetch d.user du
                    where (:appointmentDate is null or b.appointmentDate = :appointmentDate)
                      and (:status is null or s.status = :status)
                    order by b.appointmentDate desc, b.startTime desc
                    """,
            countQuery = """
                    select count(s)
                    from ConsultationSession s
                    join s.careCase c
                    join c.booking b
                    where (:appointmentDate is null or b.appointmentDate = :appointmentDate)
                      and (:status is null or s.status = :status)
                    """
    )
    Page<ConsultationSession> searchAdminSessions(
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("status") ConsultationSessionStatus status,
            Pageable pageable
    );
}
