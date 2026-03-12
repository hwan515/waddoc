package com.waddoc.domain.intake.repository;

import com.waddoc.domain.intake.entity.IntakeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntakeSessionRepository extends JpaRepository<IntakeSession, Long> {

    Optional<IntakeSession> findByPublicId(String publicId);
}
