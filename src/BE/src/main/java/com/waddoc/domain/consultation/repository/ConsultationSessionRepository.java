package com.waddoc.domain.consultation.repository;

import com.waddoc.domain.carecase.entity.CareCase;
import com.waddoc.domain.consultation.entity.ConsultationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultationSessionRepository extends JpaRepository<ConsultationSession, Long> {

    Optional<ConsultationSession> findByPublicId(String publicId);

    Optional<ConsultationSession> findByCareCase(CareCase careCase);
}
