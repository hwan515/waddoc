export type CompletionReason =
  | 'BOOKING_CREATED'
  | 'EXISTING_BOOKING_CHECKED'
  | 'USER_HANGUP'
  | 'NO_INPUT_TIMEOUT';

// IVR 상태 머신 상태
export type IntakePhase =
  | 'IDLE'
  | 'GREETING'
  | 'MENU_SELECT'
  // 신규 예약 흐름
  | 'IDENTIFY_AUTO'
  | 'IDENTIFY_BY_INPUT'
  | 'IDENTIFY_BY_VOICE'
  | 'SYMPTOM_COLLECT'
  | 'RECOMMEND_DOCTOR'
  | 'SLOT_SELECT'
  | 'BOOKING_CONFIRMED'
  // 기존 예약 조회 흐름
  | 'EXISTING_IDENTIFY'
  | 'EXISTING_IDENTIFY_BY_VOICE'
  | 'BOOKING_LOOKUP'
  | 'BOOKING_DETAIL'
  | 'BOOKING_CANCEL'
  // 종료
  | 'SESSION_END';

export type MessageRole = 'system' | 'user';

export interface ChatMessage {
  id: string;
  role: MessageRole;
  text: string;
  timestamp: Date;
  type: 'tts' | 'dtmf' | 'voice' | 'info';
}

export interface AvailableSlot {
  slotId: string;
  doctorId: string;
  doctorName: string;
  department: string;
  departmentName: string;
  date: string;
  startTime: string;
  endTime: string;
}

export interface RecommendationResult {
  recommendationId: string;
  symptomCategory: string | null;
  department: string;
  departmentName: string;
  confidenceLevel: string;
  isEmergency: boolean;
  reason: string;
  availableSlots: AvailableSlot[];
  ttsMessage: string;
}

export interface BookingResult {
  bookingId: string;
  status: string;
  caseId?: string;
  appointmentDate: string;
  startTime: string;
  endTime?: string;
  doctorName: string;
  departmentName: string;
  ttsMessage?: string;
}

export interface IdentifyResult {
  patientId: string;
  name: string;
  birthDate6: string;
  regionCode: string;
}

export interface VoiceTurnResult {
  turnId: string;
  sttText: string;
  sttConfidence: number;
  exceptionCode: string | null;
  nextAction: string;
  ttsMessage: string;
}

export interface CancelBookingResult {
  bookingId: string;
  status: string;
  cancelledAt: string | null;
  ttsMessage: string;
}
