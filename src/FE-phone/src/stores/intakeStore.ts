import { create } from 'zustand';
import type {
  IntakePhase,
  ChatMessage,
  AvailableSlot,
  BookingResult,
} from '../types/intake';
import { appendPhoneDigit } from '../utils/phoneNumber';

interface IntakeState {
  // 세션
  sessionId: string | null;
  phase: IntakePhase;
  callerPhone: string;

  // 환자 정보
  patientId: string | null;
  patientName: string | null;

  // 대화 기록
  messages: ChatMessage[];

  // 다이얼 입력 버퍼
  dialBuffer: string;

  // 추천 결과
  availableSlots: AvailableSlot[];
  currentSlotIndex: number;

  // 기존 예약
  existingBookings: BookingResult[];
  currentBookingIndex: number;

  // 로딩 상태
  isLoading: boolean;
  isSpeaking: boolean;

  // 액션
  setPhase: (phase: IntakePhase) => void;
  setSessionId: (id: string) => void;
  setCallerPhone: (phone: string) => void;
  setPatient: (id: string, name: string) => void;
  addMessage: (msg: Omit<ChatMessage, 'id' | 'timestamp'>) => void;
  appendDialBuffer: (digit: string) => void;
  clearDialBuffer: () => void;
  setAvailableSlots: (slots: AvailableSlot[]) => void;
  setCurrentSlotIndex: (idx: number) => void;
  setExistingBookings: (bookings: BookingResult[]) => void;
  setCurrentBookingIndex: (idx: number) => void;
  setIsLoading: (v: boolean) => void;
  setIsSpeaking: (v: boolean) => void;
  reset: () => void;
}

let msgCounter = 0;

const initialState = {
  sessionId: null,
  phase: 'IDLE' as IntakePhase,
  callerPhone: '',
  patientId: null,
  patientName: null,
  messages: [] as ChatMessage[],
  dialBuffer: '',
  availableSlots: [] as AvailableSlot[],
  currentSlotIndex: 0,
  existingBookings: [] as BookingResult[],
  currentBookingIndex: 0,
  isLoading: false,
  isSpeaking: false,
};

export const useIntakeStore = create<IntakeState>((set) => ({
  ...initialState,

  setPhase: (phase) => set({ phase }),
  setSessionId: (id) => set({ sessionId: id }),
  setCallerPhone: (phone) => set({ callerPhone: phone }),
  setPatient: (id, name) => set({ patientId: id, patientName: name }),

  addMessage: (msg) =>
    set((state) => ({
      messages: [
        ...state.messages,
        { ...msg, id: `msg-${++msgCounter}`, timestamp: new Date() },
      ],
    })),

  appendDialBuffer: (digit) =>
    set((state) => ({ dialBuffer: appendPhoneDigit(state.dialBuffer, digit) })),
  clearDialBuffer: () => set({ dialBuffer: '' }),

  setAvailableSlots: (slots) => set({ availableSlots: slots }),
  setCurrentSlotIndex: (idx) => set({ currentSlotIndex: idx }),
  setExistingBookings: (bookings) => set({ existingBookings: bookings }),
  setCurrentBookingIndex: (idx) => set({ currentBookingIndex: idx }),
  setIsLoading: (v) => set({ isLoading: v }),
  setIsSpeaking: (v) => set({ isSpeaking: v }),
  reset: () => set({ ...initialState, messages: [] }),
}));
