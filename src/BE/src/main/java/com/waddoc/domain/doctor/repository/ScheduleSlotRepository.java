package com.waddoc.domain.doctor.repository;

import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleSlotRepository extends JpaRepository<ScheduleSlot, Long> {

    Optional<ScheduleSlot> findByPublicId(String publicId);

    Optional<ScheduleSlot> findByDoctorUserUsernameAndSlotDateAndStartTime(
            String username, LocalDate slotDate, LocalTime startTime);

    List<ScheduleSlot> findByDoctorInAndSlotDateGreaterThanEqualAndBookedFalseOrderBySlotDateAscStartTimeAsc(
            List<DoctorProfile> doctors, LocalDate fromDate);
}
