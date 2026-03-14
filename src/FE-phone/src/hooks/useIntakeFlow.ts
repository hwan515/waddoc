import { useCallback, useEffect, useRef } from 'react';
import { useIntakeStore } from '../stores/intakeStore';
import { useTTS } from './useTTS';
import { useAudioRecorder } from './useAudioRecorder';
import * as intakeApi from '../api/intakeApi';
import type { IdentifyResult } from '../types/intake';

const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';
const DEFAULT_CALLER_PHONE =
  import.meta.env.VITE_CALLER_PHONE ?? '01012345678';

type LookupMode = 'new' | 'existing';

export function useIntakeFlow() {
  const store = useIntakeStore();
  const { speak, stop: stopTTS } = useTTS();
  const { isRecording, startRecording, stopRecording } = useAudioRecorder();

  const submitDialBufferRef = useRef<(() => void) | undefined>(undefined);
  const micToggleRef = useRef<(() => void) | undefined>(undefined);

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
    (text: string, type: 'dtmf' | 'voice' = 'dtmf') => {
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

  const promptVoiceIdentify = useCallback(
    async (mode: LookupMode) => {
      store.setPhase(
        mode === 'new' ? 'IDENTIFY_BY_VOICE' : 'EXISTING_IDENTIFY_BY_VOICE',
      );
      await systemSay(
        '등록된 번호를 찾을 수 없습니다. 이름과 생년월일 6자리를 말씀해주세요. 우물정자를 누르고 말해주세요.',
      );
    },
    [store, systemSay],
  );

  const loadExistingBookings = useCallback(
    async (sessionId: string) => {
      const bookings = await intakeApi.getExistingBookings(sessionId);
      store.setExistingBookings(bookings);

      if (bookings.length > 0) {
        store.setPhase('BOOKING_LOOKUP');
        await systemSay(
          '예약 확인은 1번, 예약 취소는 2번을 눌러주세요.',
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
      store.resetRetry();
      await intakeApi.bindPatient(sessionId, patient.patientId);

      if (mode === 'new') {
        store.setPhase('SYMPTOM_COLLECT');
        await systemSay(
          `${patient.name} 어르신, 어디가 불편하신가요? 우물정자를 누르고 말해주세요.`,
        );
        return;
      }

      await loadExistingBookings(sessionId);
    },
    [loadExistingBookings, store, systemSay],
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

  const endCall = useCallback(() => {
    stopTTS();
    store.setPhase('SESSION_END');
    store.addMessage({
      role: 'system',
      text: '통화가 종료되었습니다.',
      type: 'info',
    });
  }, [store, stopTTS]);

  const handleNewBookingFlow = useCallback(async () => {
    await handleCallerLookup('new');
  }, [handleCallerLookup]);

  const handleExistingBookingFlow = useCallback(async () => {
    await handleCallerLookup('existing');
  }, [handleCallerLookup]);

  const handleSlotSelection = useCallback(
    async (digit: string) => {
      const { availableSlots, currentSlotIndex, sessionId } =
        useIntakeStore.getState();

      if (digit === '1') {
        const slot = availableSlots[currentSlotIndex];
        store.setIsLoading(true);

        if (USE_MOCK) {
          await delay(800);
          store.setIsLoading(false);
          store.setPhase('BOOKING_CONFIRMED');
          await systemSay(
            `${slot.date} ${slot.startTime} ${slot.doctorName} 선생님 예약이 확정되었습니다. 감사합니다.`,
          );
          store.setPhase('SESSION_END');
          store.addMessage({
            role: 'system',
            text: '통화가 종료되었습니다.',
            type: 'info',
          });
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
          await systemSay(
            booking.ttsMessage ||
              `${booking.appointmentDate} ${booking.startTime} ${booking.doctorName} 선생님 예약이 확정되었습니다. 감사합니다.`,
          );
          store.setPhase('SESSION_END');
        } catch {
          store.setIsLoading(false);
          await systemSay('예약 처리 중 오류가 발생했습니다.');
        }
      } else if (digit === '2') {
        const nextIdx = currentSlotIndex + 1;
        if (nextIdx < availableSlots.length) {
          store.setCurrentSlotIndex(nextIdx);
          const slot = availableSlots[nextIdx];
          await systemSay(
            `${slot.departmentName} ${slot.doctorName} 선생님, ${slot.date} ${slot.startTime} 진료가 가능합니다. 예약하시겠습니까? 네이면 1번, 다른 시간은 2번을 눌러주세요.`,
          );
        } else {
          await systemSay(
            '더 이상 가능한 시간이 없습니다. 다시 전화해주세요. 감사합니다.',
          );
          store.setPhase('SESSION_END');
          store.addMessage({
            role: 'system',
            text: '통화가 종료되었습니다.',
            type: 'info',
          });
        }
      } else {
        await systemSay('1번 또는 2번을 눌러주세요.');
      }
    },
    [store, systemSay],
  );

  const handleBookingAction = useCallback(
    async (digit: string) => {
      const { existingBookings, sessionId } = useIntakeStore.getState();

      if (existingBookings.length === 0) {
        await systemSay('조회된 예약이 없습니다.');
        store.setPhase('SESSION_END');
        return;
      }

      const booking = existingBookings[0];

      if (digit === '1') {
        await systemSay(
          `${booking.appointmentDate} ${booking.startTime} ${booking.doctorName} 선생님 ${booking.departmentName} 예약이 있습니다.`,
        );
        store.setPhase('SESSION_END');
        store.addMessage({
          role: 'system',
          text: '통화가 종료되었습니다.',
          type: 'info',
        });
        return;
      }

      if (digit === '2') {
        if (USE_MOCK) {
          store.setIsLoading(true);
          await delay(600);
          store.setIsLoading(false);
          await systemSay('예약이 취소되었습니다. 감사합니다.');
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
            await systemSay(
              result.ttsMessage || '예약이 취소되었습니다. 감사합니다.',
            );
          } catch {
            store.setIsLoading(false);
            await systemSay('취소 처리 중 오류가 발생했습니다.');
            return;
          }
        }

        store.setPhase('SESSION_END');
        store.addMessage({
          role: 'system',
          text: '통화가 종료되었습니다.',
          type: 'info',
        });
        return;
      }

      await systemSay('1번 또는 2번을 눌러주세요.');
    },
    [store, systemSay],
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

      if (
        phase === 'IDENTIFY_BY_VOICE' ||
        phase === 'EXISTING_IDENTIFY_BY_VOICE' ||
        phase === 'SYMPTOM_COLLECT'
      ) {
        if (digit === '#') {
          micToggleRef.current?.();
          return;
        }
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

        default:
          break;
      }
    },
    [
      handleBookingAction,
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
          store.setPhase('SYMPTOM_COLLECT');
          await systemSay(
            '홍길동 어르신, 어디가 불편하신가요? 우물정자를 누르고 말해주세요.',
          );
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
        ]);
        store.setPhase('BOOKING_LOOKUP');
        await systemSay('예약 확인은 1번, 예약 취소는 2번을 눌러주세요.');
        return;
      }

      await promptVoiceIdentify(mode);
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

      await promptVoiceIdentify(mode);
    } catch {
      store.setIsLoading(false);
      await systemSay('환자 확인 중 오류가 발생했습니다. 다시 시도해주세요.');
    }
  }, [
    handleIdentifiedPatient,
    promptVoiceIdentify,
    store,
    systemSay,
    userSay,
  ]);

  const handleMicStart = useCallback(async () => {
    store.setIsRecording(true);
    await startRecording();
  }, [startRecording, store]);

  const handleMicStop = useCallback(async () => {
    const blob = await stopRecording();
    store.setIsRecording(false);
    userSay('(음성 입력)', 'voice');

    const { phase, sessionId } = useIntakeStore.getState();

    if (USE_MOCK) {
      store.setIsLoading(true);
      await delay(1200);
      store.setIsLoading(false);

      if (phase === 'IDENTIFY_BY_VOICE') {
        store.setPatient('pat_mock02', '김영희');
        store.setPhase('SYMPTOM_COLLECT');
        await systemSay(
          '김영희 어르신, 어디가 불편하신가요? 우물정자를 누르고 말해주세요.',
        );
        return;
      }

      if (phase === 'EXISTING_IDENTIFY_BY_VOICE') {
        store.setPatient('pat_mock02', '김영희');
        store.setExistingBookings([
          {
            bookingId: 'bk_mock02',
            status: 'CONFIRMED',
            appointmentDate: '2026-03-16',
            startTime: '14:00',
            endTime: '14:30',
            doctorName: '박의사',
            departmentName: '가정의학과',
          },
        ]);
        store.setPhase('BOOKING_LOOKUP');
        await systemSay('예약 확인은 1번, 예약 취소는 2번을 눌러주세요.');
        return;
      }

      if (phase === 'SYMPTOM_COLLECT') {
        store.setSymptomText('머리가 아프고 어지러워요');
        store.setPhase('RECOMMEND_DOCTOR');
        await delay(600);
        store.setAvailableSlots([
          {
            slotId: 'slot_01',
            doctorId: 'doc_01',
            doctorName: '김의사',
            department: 'INTERNAL_MEDICINE',
            departmentName: '내과',
            date: '2026-03-12',
            startTime: '10:00',
            endTime: '10:30',
          },
          {
            slotId: 'slot_02',
            doctorId: 'doc_02',
            doctorName: '박의사',
            department: 'FAMILY_MEDICINE',
            departmentName: '가정의학과',
            date: '2026-03-12',
            startTime: '14:00',
            endTime: '14:30',
          },
        ]);
        store.setCurrentSlotIndex(0);
        store.setPhase('SLOT_SELECT');
        await systemSay(
          '내과 김의사 선생님, 3월 12일 오전 10시 진료가 가능합니다. 예약하시겠습니까? 네이면 1번, 다른 시간은 2번을 눌러주세요.',
        );
      }

      return;
    }

    if (!sessionId) {
      await systemSay('세션 정보가 올바르지 않습니다. 다시 시도해주세요.');
      return;
    }

    store.setIsLoading(true);

    try {
      if (
        phase === 'IDENTIFY_BY_VOICE' ||
        phase === 'EXISTING_IDENTIFY_BY_VOICE'
      ) {
        const voiceResult = await intakeApi.submitVoiceTurn(
          sessionId,
          blob,
          '이름과 생년월일을 말씀해주세요.',
        );

        store.setIsLoading(false);

        if (voiceResult.exceptionCode) {
          store.incrementRetry();
          await systemSay(
            voiceResult.ttsMessage || '다시 한번 천천히 말씀해주세요.',
          );
          return;
        }

        const parsed = parseIdentityInfo(voiceResult.sttText);
        if (!parsed) {
          store.incrementRetry();
          await systemSay(
            '이름과 생년월일 6자리를 확인하지 못했습니다. 다시 말씀해주세요.',
          );
          return;
        }

        const identified = await intakeApi.identifyByInfo(
          sessionId,
          parsed.name,
          parsed.birthDate6,
        );

        if (!identified) {
          store.incrementRetry();
          await systemSay(
            '환자 정보를 찾지 못했습니다. 이름과 생년월일 6자리를 다시 말씀해주세요.',
          );
          return;
        }

        await handleIdentifiedPatient(
          identified,
          phase === 'IDENTIFY_BY_VOICE' ? 'new' : 'existing',
        );
        return;
      }

      if (phase === 'SYMPTOM_COLLECT') {
        const voiceResult = await intakeApi.submitVoiceTurn(
          sessionId,
          blob,
          '어디가 불편하신가요?',
        );

        if (voiceResult.exceptionCode) {
          store.setIsLoading(false);
          store.incrementRetry();
          await systemSay(
            voiceResult.ttsMessage || '다시 한번 짧게 말씀해주세요.',
          );
          return;
        }

        store.setSymptomText(voiceResult.sttText);
        store.setPhase('RECOMMEND_DOCTOR');

        const rec = await intakeApi.recommendDoctor(
          sessionId,
          voiceResult.sttText,
        );
        store.setIsLoading(false);
        store.setAvailableSlots(rec.availableSlots);
        store.setCurrentSlotIndex(0);

        if (rec.availableSlots.length > 0) {
          store.setPhase('SLOT_SELECT');
          await systemSay(rec.ttsMessage);
        } else {
          await systemSay(rec.ttsMessage);
          store.setPhase('SESSION_END');
        }
      }
    } catch {
      store.setIsLoading(false);
      await systemSay('처리 중 오류가 발생했습니다. 다시 시도해주세요.');
    }
  }, [handleIdentifiedPatient, stopRecording, store, systemSay, userSay]);

  useEffect(() => {
    submitDialBufferRef.current = submitDialBuffer;
    micToggleRef.current = isRecording ? handleMicStop : handleMicStart;
  }, [handleMicStart, handleMicStop, isRecording, submitDialBuffer]);

  return {
    startCall,
    endCall,
    handleDigit,
    submitDialBuffer,
    handleMicStart,
    handleMicStop,
    isRecording,
  };
}

function parseIdentityInfo(text: string) {
  const birthDate6 = text.match(/\d{6}/)?.[0];
  if (!birthDate6) {
    return null;
  }

  const compact = text.replace(/\s+/g, '');
  const patterns = [
    /이름(?:은|는)?([가-힣]{2,5})/,
    /([가-힣]{2,5})(?:이고|입니다|예요|이에요)/,
    /([가-힣]{2,5})/,
  ];

  for (const pattern of patterns) {
    const matched = compact.match(pattern);
    if (matched?.[1]) {
      return { name: matched[1], birthDate6 };
    }
  }

  return null;
}

function delay(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
