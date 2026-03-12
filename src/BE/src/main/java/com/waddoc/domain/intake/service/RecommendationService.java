package com.waddoc.domain.intake.service;

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
import com.waddoc.domain.intake.repository.RecommendationRepository;
import com.waddoc.domain.intake.repository.SymptomIntakeRepository;
import com.waddoc.global.error.BusinessException;
import com.waddoc.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final IntakeSessionRepository intakeSessionRepository;
    private final SymptomIntakeRepository symptomIntakeRepository;
    private final RecommendationRepository recommendationRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final ScheduleSlotRepository scheduleSlotRepository;
    private final AuditLogService auditLogService;

    /**
     * 증상 분류 → 진료과 추천 → 가용 슬롯 조회 (MVP: Mock 분류 로직)
     */
    @Transactional
    public RecommendResponse recommend(String sessionId, RecommendRequest request) {
        IntakeSession session = findActiveSession(sessionId);

        // 1. 증상 분류 (MVP Mock)
        SymptomClassification classification = classifySymptom(request.getSymptomText());

        // 2. SymptomIntake 저장
        SymptomIntake symptomIntake = SymptomIntake.builder()
                .intakeSession(session)
                .symptomText(request.getSymptomText())
                .symptomCategory(classification.category)
                .emergency(classification.emergency)
                .build();
        symptomIntakeRepository.save(symptomIntake);

        // 3. 진료과 매칭 의사 조회 + 가용 슬롯
        List<DoctorProfile> doctors = doctorProfileRepository.findByDepartment(classification.department);
        List<ScheduleSlot> slots = List.of();
        if (!doctors.isEmpty()) {
            slots = scheduleSlotRepository
                    .findByDoctorInAndSlotDateGreaterThanEqualAndBookedFalseOrderBySlotDateAscStartTimeAsc(
                            doctors, LocalDate.now());
        }

        // 4. Recommendation 저장
        Recommendation recommendation = Recommendation.builder()
                .intakeSession(session)
                .symptomIntake(symptomIntake)
                .department(classification.department)
                .departmentName(classification.departmentName)
                .confidenceLevel(classification.confidenceLevel)
                .emergency(classification.emergency)
                .reason(classification.reason)
                .build();
        recommendationRepository.save(recommendation);

        session.touch();

        // 5. 감사 로그
        String correlationId = "corr_ints_" + session.getPublicId();
        auditLogService.log(
                "SYMPTOM_CLASSIFIED",
                "INTAKE_SESSION",
                session.getPublicId(),
                correlationId,
                Map.of("recommendationId", recommendation.getPublicId(),
                       "symptomCategory", classification.category,
                       "department", classification.department,
                       "confidenceLevel", classification.confidenceLevel.name(),
                       "isEmergency", classification.emergency)
        );

        // 6. 응답 조립
        List<AvailableSlotResponse> slotResponses = slots.stream()
                .map(AvailableSlotResponse::from)
                .toList();

        String ttsMessage = buildTtsMessage(classification, slots);

        return RecommendResponse.of(recommendation, slotResponses, ttsMessage);
    }

    private IntakeSession findActiveSession(String sessionId) {
        IntakeSession session = intakeSessionRepository.findByPublicId(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTAKE_SESSION_NOT_FOUND));

        if (!session.isActive()) {
            throw new BusinessException(ErrorCode.SESSION_STATE_INVALID);
        }

        return session;
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

    private String buildTtsMessage(SymptomClassification classification, List<ScheduleSlot> slots) {
        if (classification.emergency) {
            return "응급 증상이 의심됩니다. " + classification.departmentName + " 진료를 우선 안내해 드리겠습니다.";
        }

        if (slots.isEmpty()) {
            return classification.departmentName + " 진료를 추천드리지만 현재 예약 가능한 시간이 없습니다. 다른 시간을 확인해 드릴까요?";
        }

        ScheduleSlot first = slots.get(0);
        String doctorName = first.getDoctor().getUser().getName();
        int month = first.getSlotDate().getMonthValue();
        int day = first.getSlotDate().getDayOfMonth();
        int hour = first.getStartTime().getHour();
        String amPm = hour < 12 ? "오전" : "오후";
        int displayHour = hour <= 12 ? hour : hour - 12;

        return String.format(
                "%s %s 선생님, %d월 %d일 %s %d시 진료가 가능합니다. 예약하시겠습니까? 네이면 1번, 다른 시간은 2번을 눌러주세요.",
                classification.departmentName, doctorName, month, day, amPm, displayHour);
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
}
