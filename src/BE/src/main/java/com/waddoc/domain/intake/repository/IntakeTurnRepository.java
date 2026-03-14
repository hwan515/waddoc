package com.waddoc.domain.intake.repository;

import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.IntakeTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntakeTurnRepository extends JpaRepository<IntakeTurn, Long> {

    Optional<IntakeTurn> findTopByIntakeSessionOrderByTurnOrderDesc(IntakeSession intakeSession);
}
