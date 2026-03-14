package com.waddoc.domain.intake.repository;

import com.waddoc.domain.intake.entity.IntakeSession;
import com.waddoc.domain.intake.entity.SymptomIntake;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SymptomIntakeRepository extends JpaRepository<SymptomIntake, Long> {

    Optional<SymptomIntake> findByIntakeSession(IntakeSession intakeSession);
}
