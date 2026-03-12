package com.waddoc.domain.intake.repository;

import com.waddoc.domain.intake.entity.Recommendation;
import com.waddoc.domain.intake.entity.RecommendationAvailableSlot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationAvailableSlotRepository extends JpaRepository<RecommendationAvailableSlot, Long> {

    boolean existsByRecommendationAndSlot_PublicId(Recommendation recommendation, String slotPublicId);
}
