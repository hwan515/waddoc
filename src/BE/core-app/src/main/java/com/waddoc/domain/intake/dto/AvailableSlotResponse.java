package com.waddoc.domain.intake.dto;

import com.waddoc.domain.doctor.entity.ScheduleSlot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Builder
public class AvailableSlotResponse {

    private String slotId;
    private String doctorId;
    private String doctorName;
    private String department;
    private String departmentName;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;

    public static AvailableSlotResponse from(ScheduleSlot slot) {
        return AvailableSlotResponse.builder()
                .slotId(slot.getPublicId())
                .doctorId(slot.getDoctor().getPublicId())
                .doctorName(slot.getDoctor().getUser().getName())
                .department(slot.getDoctor().getDepartment())
                .departmentName(slot.getDoctor().getDepartmentName())
                .date(slot.getSlotDate())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .build();
    }
}
