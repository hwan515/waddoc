import axios from 'axios';
import { useCallback, useEffect, useRef } from 'react';
import { useIntakeStore } from '../stores/intakeStore';
import { useTTS } from './useTTS';
import * as intakeApi from '../api/intakeApi';
import type { BookingResult, CompletionReason, IdentifyResult } from '../types/intake';

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';
const DEFAULT_CALLER_PHONE =
  import.meta.env.VITE_CALLER_PHONE ?? '01049163720';

const DEPARTMENT_MENU_MESSAGE =
  '원하시는 진료과를 선택해주세요. 내과는 1번, 피부과는 2번, 정형외과는 3번, 신경과는 4번, 안과는 5번, 다시 듣기는 0번입니다.';

const DEPARTMENT_OPTIONS = {
  '1': { code: 'INTERNAL_MEDICINE', name: '내과' },
  '2': { code: 'DERMATOLOGY', name: '피부과' },
  '3': { code: 'ORTHOPEDICS', name: '정형외과' },
  '4': { code: 'NEUROLOGY', name: '신경과' },
  '5': { code: 'OPHTHALMOLOGY', name: '안과' },
} as const;

type LookupMode = 'new' | 'existing';

interface ApiErrorResponse {
  message?: string;
  details?: Array<{
    reason?: string;
  }>;
}

function getApiErrorMessage(error: unknown, fallbackMessage: string): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const detailReason = error.response?.data?.details?.[0]?.reason?.trim();
    if (detailReason) {
      return detailReason;
    }

    const apiMessage = error.response?.data?.message?.trim();
    if (apiMessage) {
      return apiMessage;
    }

    if (error.code === 'ECONNABORTED') {
      return '요청 시간이 초과되었습니다. 다시 시도해주세요.';
    }

    if (!error.response) {
      return '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.';
    }
  }

  return fallbackMessage;
}

function getBookingSummary(
  booking: BookingResult,
  currentIndex: number,
  totalCount: number,
): string {
  const bookingOrder =
    totalCount > 1 ? `${currentIndex + 1}번째 예약입니다. ` : '';

  return (
    `${bookingOrder}${booking.appointmentDate} ${booking.startTime} ` +
    `${booking.doctorName} 선생님 ${booking.departmentName} 예약이 있습니다.`
  );
}

function getBookingLookupPrompt(
  booking: BookingResult,
  currentIndex: number,
  totalCount: number,
): string {
  const options =
    totalCount > 1
      ? '예약 확인은 1번, 예약 취소는 2번, 다음 예약은 3번, 다시 듣기는 0번을 눌러주세요.'
      : '예약 확인은 1번, 예약 취소는 2번, 다시 듣기는 0번을 눌러주세요.';

  return `${getBookingSummary(booking, currentIndex, totalCount)} ${options}`;
}

export function useIntakeFlow() {
  const store = useIntakeStore();
  const { speak, stop: stopTTS } = useTTS();

  const submitDialBufferRef = useRef<(() => void) | undefined>(undefined);

  const systemSay = useCallback(
    async (text: string) => {
      store.addMessage({ role: 'system', text, type: 'tts' });
      store.setIsSpeaking(true);
      await speak(text);
      store.setIsSpeaking(false);
    },
    [speak, store],
  );

  const userSay = useCallback(
    (text: string, type: 'dtmf' = 'dtmf') => {
      store.addMessage({ role: 'user', text, type });
    },
    [store],
  );

  const promptPhoneInput = useCallback(
    async (mode: LookupMode) => {
      store.setPhase(mode === 'new' ? 'IDENTIFY_BY_INPUT' : 'EXISTING_IDENTIFY');
      store.clearDialBuffer();
      await systemSay(
        '등록된 전화번호를 입력해주세요. 입력 후 우물정자를 눌러주세요.',
      );
    },
    [store, systemSay],
  );

  const promptDepartmentSelection = useCallback(async () => {
    store.setPhase('DEPARTMENT_SELECT');
    await systemSay(DEPARTMENT_MENU_MESSAGE);
  }, [store, systemSay]);

  const loadExistingBookings = useCallback(
    async (sessionId: string) => {
      const bookings = await intakeApi.getExistingBookings(sessionId);
      store.setExistingBookings(bookings);
      store.setCurrentBookingIndex(0);

      if (bookings.length > 0) {
        store.setPhase('BOOKING_LOOKUP');
        await systemSay(
          getBookingLookupPrompt(bookings[0], 0, bookings.length),
        );
        return;
      }

      store.setPhase('MENU_SELECT');
      await systemSay(
        '조회된 예약이 없습니다. 새로운 예약을 원하시면 1번을 눌러주세요.',
      );
    },
    [store, systemSay],
  );

  const handleIdentifiedPatient = useCallback(
    async (patient: IdentifyResult, mode: LookupMode) => {
      const { sessionId } = useIntakeStore.getState();
      if (!sessionId) {
        throw new Error('intake session is missing');
      }

      store.setPatient(patient.patientId, patient.name);
      await intakeApi.bindPatient(sessionId, patient.patientId);

      if (mode === 'new') {
        await promptDepartmentSelection();
        return;
      }

      await loadExistingBookings(sessionId);
    },
    [loadExistingBookings, promptDepartmentSelection, store],
  );

  const handleCallerLookup = useCallback(
    async (mode: LookupMode) => {
      const { sessionId, callerPhone } = useIntakeStore.getState();
      if (!sessionId) {
        await promptPhoneInput(mode);
        return;
      }

      if (USE_MOCK) {
        await promptPhoneInput(mode);
        return;
      }

      store.setIsLoading(true);
      try {
        const identified = await intakeApi.identifyByCallerNumber(
          sessionId,
          callerPhone || DEFAULT_CALLER_PHONE,
        );
        store.setIsLoading(false);

        if (identified) {
          await handleIdentifiedPatient(identified, mode);
          return;
        }
      } catch {
        store.setIsLoading(false);
      }

      await promptPhoneInput(mode);
    },
    [handleIdentifiedPatient, promptPhoneInput, store],
  );

  const startCall = useCallback(async () => {
    const callerPhone =
      useIntakeStore.getState().callerPhone || DEFAULT_CALLER_PHONE;

    store.reset();
    store.setCallerPhone(callerPhone);

    if (!USE_MOCK) {
      store.setIsLoading(true);
      try {
        const { intakeSessionId } = await intakeApi.createSession(callerPhone);
        store.setSessionId(intakeSessionId);
      } catch {
        store.addMessage({
          role: 'system',
          text: '연결에 실패했습니다. 다시 시도해주세요.',
          type: 'info',
        });
        store.setIsLoading(false);
        return;
      }
      store.setIsLoading(false);
    } else {
      store.setSessionId('mock-session-001');
    }

    store.setPhase('GREETING');
    await systemSay('안녕하세요, 왔닥입니다.');
    store.setPhase('MENU_SELECT');
    await systemSay(
      '새로운 예약을 원하시면 1번, 기존 예약 조회·취소를 원하시면 2번을 눌러주세요.',
    );
  }, [store, systemSay]);

  const finishSession = useCallback(
    async (reason?: CompletionReason, finalMessage?: string) => {
      stopTTS();

      if (finalMessage) {
        await systemSay(finalMessage);
      }

      const { sessionId } = useIntakeStore.getState();
      if (!USE_MOCK && sessionId && reason) {
        try {
          await intakeApi.completeSession(sessionId, reason);
        } catch {
          // 세션 종료 기록 실패가 UI 종료를 막지 않도록 무시한다.
        }
      }

      store.setPhase('SESSION_END');
      store.addMessage({
        role: 'system',
        text: '통화가 종료되었습니다.',
        type: 'info',
      });
    },
    [stopTTS, store, systemSay],
  );

  const endCall = useCallback(() => {
    const { phase } = useIntakeStore.getState();
    if (phase === 'SESSION_END') return;
    finishSession('USER_HANGUP');
  }, [finishSession]);

  const handleNewBookingFlow = useCallback(async () => {
    await handleCallerLookup('new');
  }, [handleCallerLookup]);

  const handleExistingBookingFlow = useCallback(async () => {
    await handleCallerLookup('existing');
  }, [handleCallerLookup]);

  const handleDepartmentSelection = useCallback(
    async (digit: string) => {
      if (digit === '0') {
        await systemSay(DEPARTMENT_MENU_MESSAGE);
        return;
      }

      const selectedDepartment = DEPARTMENT_OPTIONS[
        digit as keyof typeof DEPARTMENT_OPTIONS
      ];
      if (!selectedDepartment) {
        await systemSay(
          '내과는 1번, 피부과는 2번, 정형외과는 3번, 신경과는 4번, 안과는 5번, 다시 듣기는 0번입니다.',
        );
        return;
      }

      const { sessionId } = useIntakeStore.getState();
      if (!sessionId) {
        await systemSay('세션 정보가 올바르지 않습니다. 다시 시도해주세요.');
        return;
      }

      store.setPhase('RECOMMEND_DOCTOR');
      store.setIsLoading(true);

      if (USE_MOCK) {
        await delay(800);
        store.setIsLoading(false);

        const rec = {
          symptomCategory: null,
          department: selectedDepartment.code,
          departmentName: selectedDepartment.name,
          confidenceLevel: 'HIGH',
          isEmergency: false,
          reason: `${selectedDepartment.name} 진료과를 직접 선택했습니다.`,
          availableSlots: [
            {
              slotId: `slot_${digit}_01`,
              doctorId: `doc_${digit}_01`,
              doctorName: `${selectedDepartment.name} 김의사`,
              department: selectedDepartment.code,
              departmentName: selectedDepartment.name,
              date: '2026-03-12',
              startTime: '10:00',
              endTime: '10:30',
            },
            {
              slotId: `slot_${digit}_02`,
              doctorId: `doc_${digit}_02`,
              doctorName: `${selectedDepartment.name} 박의사`,
              department: selectedDepartment.code,
              departmentName: selectedDepartment.name,
              date: '2026-03-12',
              startTime: '14:00',
              endTime: '14:30',
            },
          ],
          ttsMessage: `${selectedDepartment.name} 김의사 선생님, 3월 12일 오전 10시 진료가 가능합니다. 예약은 1번, 다른 시간은 2번, 다시 듣기는 0번입니다.`,
        };

        store.setAvailableSlots(rec.availableSlots);
        store.setCurrentSlotIndex(0);
        store.setPhase('SLOT_SELECT');
        await systemSay(rec.ttsMessage);
        return;
      }

      try {
        const rec = await intakeApi.recommendDoctor(
          sessionId,
          selectedDepartment.code,
        );
        store.setIsLoading(false);
        store.setAvailableSlots(rec.availableSlots);
        store.setCurrentSlotIndex(0);

        if (rec.availableSlots.length > 0) {
          store.setPhase('SLOT_SELECT');
          await systemSay(rec.ttsMessage);
          return;
        }

        await finishSession(undefined, rec.ttsMessage);
      } catch (error) {
        store.setIsLoading(false);
        await systemSay(
          getApiErrorMessage(
            error,
            '진료과 매칭 중 오류가 발생했습니다. 다시 시도해주세요.',
          ),
        );
      }
    },
    [finishSession, store, systemSay],
  );

  const handleSlotSelection = useCallback(
    async (digit: string) => {
      const { availableSlots, currentSlotIndex, sessionId } =
        useIntakeStore.getState();
      const slot = availableSlots[currentSlotIndex];

      if (digit === '0') {
        if (!slot) {
          await systemSay('현재 안내 가능한 예약 시간이 없습니다.');
          return;
        }

        await systemSay(
          `${slot.departmentName} ${slot.doctorName} 선생님, ${slot.date} ${slot.startTime} 진료가 가능합니다. 예약은 1번, 다른 시간은 2번, 다시 듣기는 0번입니다.`,
        );
        return;
      }

      if (digit === '1') {
        store.setIsLoading(true);

        if (USE_MOCK) {
          await delay(800);
          store.setIsLoading(false);
          store.setPhase('BOOKING_CONFIRMED');
          await finishSession(
            'BOOKING_CREATED',
            `${slot.date} ${slot.startTime} ${slot.doctorName} 선생님 예약이 확정되었습니다. 감사합니다.`,
          );
          return;
        }

        if (!sessionId) {
          store.setIsLoading(false);
          await systemSay(
            '세션 정보가 올바르지 않습니다. 다시 시도해주세요.',
          );
          return;
        }

        try {
          const booking = await intakeApi.createBooking(sessionId, slot.slotId);
          store.setIsLoading(false);
          store.setPhase('BOOKING_CONFIRMED');
          await finishSession(
            'BOOKING_CREATED',
            booking.ttsMessage ||
              `${booking.appointmentDate} ${booking.startTime} ${booking.doctorName} 선생님 예약이 확정되었습니다. 감사합니다.`,
          );
        } catch (error) {
          store.setIsLoading(false);
          await systemSay(
            getApiErrorMessage(error, '예약 처리 중 오류가 발생했습니다.'),
          );
        }
      } else if (digit === '2') {
        const nextIdx = currentSlotIndex + 1;
        if (nextIdx < availableSlots.length) {
          store.setCurrentSlotIndex(nextIdx);
          const nextSlot = availableSlots[nextIdx];
          await systemSay(
            `${nextSlot.departmentName} ${nextSlot.doctorName} 선생님, ${nextSlot.date} ${nextSlot.startTime} 진료가 가능합니다. 예약은 1번, 다른 시간은 2번, 다시 듣기는 0번입니다.`,
          );
        } else {
          // TODO: BE에 NO_AVAILABLE_SLOT 같은 종료 사유가 추가되면 전용 completionReason을 전달하도록 변경한다.
          await finishSession(
            undefined,
            '더 이상 가능한 시간이 없습니다. 다시 전화해주세요. 감사합니다.',
          );
        }
      } else {
        await systemSay('예약은 1번, 다른 시간은 2번, 다시 듣기는 0번입니다.');
      }
    },
    [finishSession, store, systemSay],
  );

  const handleBookingAction = useCallback(
    async (digit: string) => {
      const { existingBookings, currentBookingIndex, sessionId } =
        useIntakeStore.getState();

      if (existingBookings.length === 0) {
        await finishSession('EXISTING_BOOKING_CHECKED', '조회된 예약이 없습니다.');
        return;
      }

      const booking = existingBookings[currentBookingIndex];
      if (!booking) {
        await systemSay('예약 정보가 올바르지 않습니다. 다시 시도해주세요.');
        return;
      }

      if (digit === '0') {
        await systemSay(
          getBookingLookupPrompt(
            booking,
            currentBookingIndex,
            existingBookings.length,
          ),
        );
        return;
      }

      if (digit === '1') {
        await finishSession(
          'EXISTING_BOOKING_CHECKED',
          getBookingSummary(
            booking,
            currentBookingIndex,
            existingBookings.length,
          ),
        );
        return;
      }

      if (digit === '2') {
        if (USE_MOCK) {
          store.setIsLoading(true);
          await delay(600);
          store.setIsLoading(false);
          await finishSession(
            'EXISTING_BOOKING_CHECKED',
            '예약이 취소되었습니다. 감사합니다.',
          );
        } else {
          if (!sessionId) {
            await systemSay(
              '세션 정보가 올바르지 않습니다. 다시 시도해주세요.',
            );
            return;
          }

          store.setIsLoading(true);
          try {
            const result = await intakeApi.cancelBooking(
              sessionId,
              booking.bookingId,
            );
            store.setIsLoading(false);
            await finishSession(
              'EXISTING_BOOKING_CHECKED',
              result.ttsMessage || '예약이 취소되었습니다. 감사합니다.',
            );
          } catch {
            store.setIsLoading(false);
            await systemSay('취소 처리 중 오류가 발생했습니다.');
            return;
          }
        }
        return;
      }

      if (digit === '3' && existingBookings.length > 1) {
        const nextIndex = (currentBookingIndex + 1) % existingBookings.length;
        const nextBooking = existingBookings[nextIndex];
        store.setCurrentBookingIndex(nextIndex);

        const wrappedToFirst = nextIndex === 0;
        const prefix = wrappedToFirst
          ? '마지막 예약입니다. 첫 번째 예약을 다시 안내합니다. '
          : '';

        await systemSay(
          prefix +
            getBookingLookupPrompt(
              nextBooking,
              nextIndex,
              existingBookings.length,
            ),
        );
        return;
      }

      await systemSay(
        existingBookings.length > 1
          ? '예약 확인은 1번, 예약 취소는 2번, 다음 예약은 3번, 다시 듣기는 0번입니다.'
          : '예약 확인은 1번, 예약 취소는 2번, 다시 듣기는 0번입니다.',
      );
    },
    [finishSession, store, systemSay],
  );

  const handleDigit = useCallback(
    async (digit: string) => {
      const { phase } = useIntakeStore.getState();

      if (phase === 'IDENTIFY_BY_INPUT' || phase === 'EXISTING_IDENTIFY') {
        if (digit === '#') {
          submitDialBufferRef.current?.();
          return;
        }
        store.appendDialBuffer(digit);
        return;
      }

      userSay(digit);

      switch (phase) {
        case 'MENU_SELECT': {
          if (digit === '1') {
            store.setPhase('IDENTIFY_AUTO');
            await handleNewBookingFlow();
          } else if (digit === '2') {
            await handleExistingBookingFlow();
          } else {
            await systemSay('1번 또는 2번을 눌러주세요.');
          }
          break;
        }

        case 'SLOT_SELECT': {
          await handleSlotSelection(digit);
          break;
        }

        case 'BOOKING_LOOKUP': {
          await handleBookingAction(digit);
          break;
        }

        case 'DEPARTMENT_SELECT': {
          await handleDepartmentSelection(digit);
          break;
        }

        default:
          break;
      }
    },
    [
      handleBookingAction,
      handleDepartmentSelection,
      handleExistingBookingFlow,
      handleNewBookingFlow,
      handleSlotSelection,
      store,
      systemSay,
      userSay,
    ],
  );

  const submitDialBuffer = useCallback(async () => {
    const { dialBuffer, phase, sessionId } = useIntakeStore.getState();
    if (!dialBuffer) {
      return;
    }

    const mode: LookupMode =
      phase === 'EXISTING_IDENTIFY' ? 'existing' : 'new';

    userSay(dialBuffer, 'dtmf');
    store.clearDialBuffer();

    if (USE_MOCK) {
      store.setIsLoading(true);
      await delay(800);
      store.setIsLoading(false);

      if (dialBuffer === DEFAULT_CALLER_PHONE) {
        if (mode === 'new') {
          store.setPatient('pat_mock01', '홍길동');
          await promptDepartmentSelection();
          return;
        }

        store.setPatient('pat_mock01', '홍길동');
        store.setExistingBookings([
          {
            bookingId: 'bk_mock01',
            status: 'CONFIRMED',
            appointmentDate: '2026-03-15',
            startTime: '10:00',
            endTime: '10:30',
            doctorName: '김의사',
            departmentName: '내과',
          },
          {
            bookingId: 'bk_mock02',
            status: 'CONFIRMED',
            appointmentDate: '2026-03-20',
            startTime: '14:00',
            endTime: '14:30',
            doctorName: '박의사',
            departmentName: '피부과',
          },
        ]);
        store.setCurrentBookingIndex(0);
        store.setPhase('BOOKING_LOOKUP');
        await systemSay(
          getBookingLookupPrompt(
            {
              bookingId: 'bk_mock01',
              status: 'CONFIRMED',
              appointmentDate: '2026-03-15',
              startTime: '10:00',
              endTime: '10:30',
              doctorName: '김의사',
              departmentName: '내과',
            },
            0,
            2,
          ),
        );
        return;
      }

      await promptPhoneInput(mode);
      return;
    }

    if (!sessionId) {
      await systemSay('세션 정보가 올바르지 않습니다. 다시 시도해주세요.');
      return;
    }

    store.setIsLoading(true);
    try {
      const identified = await intakeApi.identifyByPhone(sessionId, dialBuffer);
      store.setIsLoading(false);

      if (identified) {
        await handleIdentifiedPatient(identified, mode);
        return;
      }

      await promptPhoneInput(mode);
    } catch (error) {
      store.setIsLoading(false);
      await systemSay(
        getApiErrorMessage(
          error,
          '환자 확인 중 오류가 발생했습니다. 다시 시도해주세요.',
        ),
      );
    }
  }, [
    handleIdentifiedPatient,
    promptDepartmentSelection,
    promptPhoneInput,
    store,
    systemSay,
    userSay,
  ]);

  useEffect(() => {
    submitDialBufferRef.current = submitDialBuffer;
  }, [submitDialBuffer]);

  return {
    startCall,
    endCall,
    handleDigit,
    submitDialBuffer,
  };
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
