package com.waddoc.domain.intake.service;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.booking.repository.BookingRepository;
import com.waddoc.domain.audit.service.AuditLogService;
import com.waddoc.domain.doctor.entity.DoctorProfile;
import com.waddoc.domain.doctor.entity.ScheduleSlot;
import com.waddoc.domain.doctor.repository.DoctorProfileRepository;
import com.waddoc.domain.doctor.repository.ScheduleSlotRepository;
import com.waddoc.domain.intake.dto.AvailableSlotResponse;
import com.waddoc.domain.intake.dto.RecommendRequest;
import com.waddoc.domain.intake.dto.RecommendResponse;
import com.waddoc.domain.intake.entity.*;
import com.waddoc.domain.intake.repository.IntakeSessionRepository;
import com.waddoc.domain.patient.entity.Patient;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 문진 결과를 바탕으로 진료과를 정하고 예약 가능한 슬롯을 추천한다.
 */
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final IntakeSessionRepository intakeSessionRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final BookingRepository bookingRepository;
    private final AuditLogService auditLogService;

    /**
     * DTMF 진료과 선택 또는 증상 입력을 기반으로 추천 → 가용 슬롯 조회.
     */
    @Transactional
    public RecommendResponse recommend(String sessionId, RecommendRequest request) {
        IntakeSession session = findActiveSession(sessionId);
        Patient patient = session.getPatient();
        if (patient == null) {
            throw new BusinessException(ErrorCode.PATIENT_NOT_BOUND);
        }

        RecommendationSelection selection = resolveSelection(request);

        // 1. 진료과 매칭 의사 조회 + 가용 슬롯
        List<DoctorProfile> doctors = doctorProfileRepository.findByDepartment(selection.department);
        DoctorProfile preferredDoctor = findPreferredDoctor(patient, selection.department).orElse(null);
        List<ScheduleSlot> slots = List.of();
        if (!doctors.isEmpty()) {
            slots = prioritizeSlotsByPreferredDoctor(scheduleSlotRepository
                    .findByDoctorInAndSlotDateGreaterThanEqualAndBookedFalseOrderBySlotDateAscStartTimeAsc(
                            doctors, LocalDate.now()), preferredDoctor);
        }

        String reason = buildRecommendationReason(selection.reason, preferredDoctor, slots);

        // 2. 세션에 과 선택 결과 + 슬롯 스냅샷 저장
        List<String> slotPublicIds = slots.stream()
                .map(ScheduleSlot::getPublicId)
                .toList();

        session.recordSelection(
                selection.department,
                selection.departmentName,
                selection.confidenceLevel,
                selection.emergency,
                reason,
                slotPublicIds
        );

        session.touch();

        // 3. 감사 로그
        String correlationId = "corr_ints_" + session.getPublicId();
        Map<String, Object> detailJson = new LinkedHashMap<>();
        detailJson.put("sessionId", session.getPublicId());
        if (selection.category != null) {
            detailJson.put("symptomCategory", selection.category);
        }
        detailJson.put("department", selection.department);
        detailJson.put("confidenceLevel", selection.confidenceLevel.name());
        detailJson.put("isEmergency", selection.emergency);
        if (preferredDoctor != null) {
            detailJson.put("preferredDoctorId", preferredDoctor.getPublicId());
        }
        auditLogService.log("SYMPTOM_CLASSIFIED", "INTAKE_SESSION", session.getPublicId(), correlationId, detailJson);

        // 4. 응답 조립
        List<AvailableSlotResponse> slotResponses = slots.stream()
                .map(AvailableSlotResponse::from)
                .toList();

        String ttsMessage = buildTtsMessage(selection, slots);

        return RecommendResponse.of(
                selection.category,
                selection.department,
                selection.departmentName,
                selection.confidenceLevel,
                selection.emergency,
                reason,
                slotResponses,
                ttsMessage
        );
    }

    private IntakeSession findActiveSession(String sessionId) {
        IntakeSession session = intakeSessionRepository.findByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        return session;
    }

    private RecommendationSelection resolveSelection(RecommendRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (request.getDepartmentCode() != null && !request.getDepartmentCode().isBlank()) {
            return selectDepartment(request.getDepartmentCode().trim().toUpperCase());
        }

        if (request.getSymptomText() != null && !request.getSymptomText().isBlank()) {
            SymptomClassification classification = classifySymptom(request.getSymptomText());
            return RecommendationSelection.fromClassification(classification);
        }

        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    private RecommendationSelection selectDepartment(String departmentCode) {
        return switch (departmentCode) {
            case "INTERNAL_MEDICINE" -> new RecommendationSelection(
                    "DTMF_SELECTION",
                    "INTERNAL_MEDICINE",
                    "내과",
                    ConfidenceLevel.HIGH,
                    false,
                    "환자가 내과를 직접 선택했습니다."
            );
            case "DERMATOLOGY" -> new RecommendationSelection(
                    "DTMF_SELECTION",
                    "DERMATOLOGY",
                    "피부과",
                    ConfidenceLevel.HIGH,
                    false,
                    "환자가 피부과를 직접 선택했습니다."
            );
            case "ORTHOPEDICS" -> new RecommendationSelection(
                    "DTMF_SELECTION",
                    "ORTHOPEDICS",
                    "정형외과",
                    ConfidenceLevel.HIGH,
                    false,
                    "환자가 정형외과를 직접 선택했습니다."
            );
            case "NEUROLOGY" -> new RecommendationSelection(
                    "DTMF_SELECTION",
                    "NEUROLOGY",
                    "신경과",
                    ConfidenceLevel.HIGH,
                    false,
                    "환자가 신경과를 직접 선택했습니다."
            );
            case "OPHTHALMOLOGY" -> new RecommendationSelection(
                    "DTMF_SELECTION",
                    "OPHTHALMOLOGY",
                    "안과",
                    ConfidenceLevel.HIGH,
                    false,
                    "환자가 안과를 직접 선택했습니다."
            );
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT);
        };
    }

    private java.util.Optional<DoctorProfile> findPreferredDoctor(Patient patient, String department) {
        return bookingRepository
                .findRecentPastDepartmentBookings(
                        patient,
                        department,
                        BookingStatus.CANCELLED,
                        LocalDate.now(),
                        LocalTime.now(),
                        PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .map(Booking::getDoctor);
    }

    private List<ScheduleSlot> prioritizeSlotsByPreferredDoctor(List<ScheduleSlot> slots, DoctorProfile preferredDoctor) {
        if (preferredDoctor == null || slots.isEmpty()) {
            return slots;
        }

        List<ScheduleSlot> preferredSlots = slots.stream()
                .filter(slot -> slot.getDoctor().getPublicId().equals(preferredDoctor.getPublicId()))
                .toList();
        if (preferredSlots.isEmpty()) {
            return slots;
        }

        List<ScheduleSlot> prioritized = new ArrayList<>(preferredSlots);
        slots.stream()
                .filter(slot -> !slot.getDoctor().getPublicId().equals(preferredDoctor.getPublicId()))
                .forEach(prioritized::add);
        return prioritized;
    }

    private String buildRecommendationReason(String baseReason, DoctorProfile preferredDoctor, List<ScheduleSlot> slots) {
        if (preferredDoctor == null) {
            return baseReason;
        }

        boolean preferredDoctorAvailable = slots.stream()
                .anyMatch(slot -> slot.getDoctor().getPublicId().equals(preferredDoctor.getPublicId()));

        if (preferredDoctorAvailable) {
            return baseReason + " 같은 진료과의 최근 담당 의사를 우선 매칭했습니다.";
        }

        return baseReason + " 최근 담당 의사 가용 슬롯이 없어 같은 진료과의 다른 의사로 안내합니다.";
    }

    /**
     * MVP Mock 증상 분류: 키워드 기반 규칙 매칭.
     * 실제 AI 모델 연동 시 이 메서드만 교체하면 됨.
     */
    private SymptomClassification classifySymptom(String symptomText) {
        String text = symptomText.toLowerCase();

        if (text.contains("흉통") || text.contains("가슴") && text.contains("아프")) {
            return new SymptomClassification("흉통", "CARDIOLOGY", "심장내과",
                    ConfidenceLevel.HIGH, true, "흉통 증상으로 심장내과 진료 추천 (응급 의심)");
        }
        if (text.contains("머리") && (text.contains("아프") || text.contains("아파"))) {
            if (text.contains("열") || text.contains("발열")) {
                return new SymptomClassification("두통/발열", "INTERNAL_MEDICINE", "내과",
                        ConfidenceLevel.HIGH, false, "두통 + 발열 증상으로 내과 진료 추천");
            }
            return new SymptomClassification("두통", "NEUROLOGY", "신경과",
                    ConfidenceLevel.MEDIUM, false, "두통 증상으로 신경과 진료 추천");
        }
        if (text.contains("열") || text.contains("발열") || text.contains("감기") || text.contains("기침")) {
            return new SymptomClassification("발열/감기", "INTERNAL_MEDICINE", "내과",
                    ConfidenceLevel.HIGH, false, "발열/감기 증상으로 내과 진료 추천");
        }
        if (text.contains("배") && (text.contains("아프") || text.contains("아파")) || text.contains("복통")) {
            return new SymptomClassification("복통", "INTERNAL_MEDICINE", "내과",
                    ConfidenceLevel.MEDIUM, false, "복통 증상으로 내과 진료 추천");
        }
        if (text.contains("허리") || text.contains("무릎") || text.contains("관절")) {
            return new SymptomClassification("근골격계 통증", "ORTHOPEDICS", "정형외과",
                    ConfidenceLevel.HIGH, false, "근골격계 통증으로 정형외과 진료 추천");
        }
        if (text.contains("피부") || text.contains("발진") || text.contains("두드러기")) {
            return new SymptomClassification("피부 증상", "DERMATOLOGY", "피부과",
                    ConfidenceLevel.MEDIUM, false, "피부 증상으로 피부과 진료 추천");
        }
        if (text.contains("눈") && (text.contains("아프") || text.contains("침침") || text.contains("충혈"))) {
            return new SymptomClassification("안과 증상", "OPHTHALMOLOGY", "안과",
                    ConfidenceLevel.MEDIUM, false, "안과 증상으로 안과 진료 추천");
        }

        // 기본값: 내과
        return new SymptomClassification("기타", "INTERNAL_MEDICINE", "내과",
                ConfidenceLevel.LOW, false, "증상 분류가 명확하지 않아 내과 진료를 우선 추천합니다.");
    }

    private String buildTtsMessage(RecommendationSelection selection, List<ScheduleSlot> slots) {
        if (selection.emergency) {
            return "응급 증상이 의심됩니다. " + selection.departmentName + " 진료를 우선 안내해 드리겠습니다.";
        }

        if (slots.isEmpty()) {
            return selection.departmentName + " 진료과는 현재 예약 가능한 시간이 없습니다. 다시 시도해주세요.";
        }

        ScheduleSlot first = slots.get(0);
        String doctorName = first.getDoctor().getUser().getName();
        int month = first.getSlotDate().getMonthValue();
        int day = first.getSlotDate().getDayOfMonth();

        return String.format(
                "%s %s 선생님, %d월 %d일 %s 진료가 가능합니다. 예약은 1번, 다른 시간은 2번, 다시 듣기는 0번입니다.",
                selection.departmentName, doctorName, month, day, formatTimeForTts(first.getStartTime()));
    }

    private String formatTimeForTts(LocalTime time) {
        int hour = time.getHour();
        String amPm = hour < 12 ? "오전" : "오후";
        int displayHour = hour == 0 ? 12 : (hour <= 12 ? hour : hour - 12);
        int minute = time.getMinute();

        // 추천 음성도 예약 음성과 동일한 기준으로 읽어야 시간 오해가 생기지 않는다.
        if (minute == 0) {
            return String.format("%s %d시", amPm, displayHour);
        }
        return String.format("%s %d시 %d분", amPm, displayHour, minute);
    }

    /** MVP Mock 증상 분류 결과 내부 구조체 */
    private record SymptomClassification(
            String category,
            String department,
            String departmentName,
            ConfidenceLevel confidenceLevel,
            boolean emergency,
            String reason
    ) {}

    private record RecommendationSelection(
            String category,
            String department,
            String departmentName,
            ConfidenceLevel confidenceLevel,
            boolean emergency,
            String reason
    ) {
        private static RecommendationSelection fromClassification(SymptomClassification classification) {
            return new RecommendationSelection(
                    classification.category,
                    classification.department,
                    classification.departmentName,
                    classification.confidenceLevel,
                    classification.emergency,
                    classification.reason
            );
        }
    }
}
