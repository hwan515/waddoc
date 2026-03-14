package com.waddoc.domain.intake.repository;

import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.Recommendation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RecommendationRepository extends JpaRepository<Recommendation, Long> {

    Optional<Recommendation> findByPublicId(String publicId);

    Optional<Recommendation> findByIntakeSession(IntakeSession intakeSession);
}
