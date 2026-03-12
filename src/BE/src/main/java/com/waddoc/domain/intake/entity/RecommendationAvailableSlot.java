package com.waddoc.domain.intake.entity;

import com.waddoc.domain.doctor.entity.ScheduleSlot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "recommendation_available_slot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationAvailableSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommendation_id", nullable = false)
    private Recommendation recommendation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private ScheduleSlot slot;

    @Builder
    public RecommendationAvailableSlot(Recommendation recommendation, ScheduleSlot slot) {
        this.recommendation = recommendation;
        this.slot = slot;
    }
}
