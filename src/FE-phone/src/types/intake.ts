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
  date: string;
  startTime: string;
  endTime: string;
}

export interface RecommendationResult {
  recommendationId: string;
  department: string;
  departmentName: string;
  confidenceLevel: string;
  reason: string;
  availableSlots: AvailableSlot[];
}

export interface BookingResult {
  bookingId: string;
  status: string;
  appointmentDate: string;
  startTime: string;
  doctorName: string;
  departmentName: string;
}

export interface IdentifyResult {
  patientId: string;
  name: string;
  birthDate: string;
  phone: string;
}

export interface VoiceTurnResult {
  turnId: string;
  sttText: string;
  sttConfidence: number;
  exceptionCode: string | null;
  nextAction: string;
  ttsMessage: string;
}
