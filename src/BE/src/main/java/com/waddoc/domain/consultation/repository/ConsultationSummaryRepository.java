package com.waddoc.domain.consultation.repository;

import com.waddoc.domain.consultation.entity.ConsultationSession;
import com.waddoc.domain.consultation.entity.ConsultationSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsultationSummaryRepository extends JpaRepository<ConsultationSummary, Long> {

    Optional<ConsultationSummary> findBySession(ConsultationSession session);
}
