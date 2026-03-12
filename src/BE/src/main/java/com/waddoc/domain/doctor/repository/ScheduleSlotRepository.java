package com.waddoc.domain.doctor.repository;

import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {

    Optional<ScheduleSlot> findByPublicId(String publicId);

    List<ScheduleSlot> findByDoctorInAndSlotDateGreaterThanEqualAndBookedFalseOrderBySlotDateAscStartTimeAsc(
            List<DoctorProfile> doctors, LocalDate fromDate);
}
