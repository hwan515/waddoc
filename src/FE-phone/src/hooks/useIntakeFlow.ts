import { useCallback, useEffect, useRef } from 'react';
import { useIntakeStore } from '../stores/intakeStore';
import { useTTS } from './useTTS';
import { useAudioRecorder } from './useAudioRecorder';
import * as intakeApi from '../api/intakeApi';

// 백엔드 미구현 시 목업 동작을 위한 플래그
const USE_MOCK = import.meta.env.VITE_USE_MOCK !== 'false';

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

  // ─── 전화 걸기 ───
  const startCall = useCallback(async () => {
    store.reset();

    if (!USE_MOCK) {
      store.setIsLoading(true);
      try {
        const { intakeSessionId } = await intakeApi.createSession();
        store.setSessionId(intakeSessionId);
      } catch {
        store.addMessage({ role: 'system', text: '연결에 실패했습니다. 다시 시도해주세요.', type: 'info' });
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
    await systemSay('새로운 예약을 원하시면 1번, 기존 예약 조회·취소를 원하시면 2번을 눌러주세요.');
  }, [store, systemSay]);

  // ─── 전화 끊기 ───
  const endCall = useCallback(() => {
    stopTTS();
    store.setPhase('SESSION_END');
    store.addMessage({ role: 'system', text: '통화가 종료되었습니다.', type: 'info' });
  }, [store, stopTTS]);

  // ─── DTMF 다이얼 입력 ───
  const handleDigit = useCallback(
    async (digit: string) => {
      const { phase } = useIntakeStore.getState();

      // 번호 입력 모드: 버퍼에 축적
      if (phase === 'IDENTIFY_BY_INPUT') {
        if (digit === '#') {
          submitDialBufferRef.current?.();
          return;
        }
        store.appendDialBuffer(digit);
        return;
      }

      // 음성 입력 모드: # 누르면 마이크 토글
      if (phase === 'IDENTIFY_BY_VOICE' || phase === 'SYMPTOM_COLLECT') {
        if (digit === '#') {
          micToggleRef.current?.();
          return;
        }
      }

      // 메뉴 선택 등: 즉시 처리
      userSay(digit);

      switch (phase) {
        case 'MENU_SELECT': {
          if (digit === '1') {
            store.setPhase('IDENTIFY_AUTO');
            await handleNewBookingFlow();
          } else if (digit === '2') {
            store.setPhase('EXISTING_IDENTIFY');
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
    [store, userSay, systemSay],
  );

  // ─── 번호 입력 전송 (전화번호 입력 후 #) ───
  const submitDialBuffer = useCallback(async () => {
    const { dialBuffer } = useIntakeStore.getState();
    if (!dialBuffer) return;

    userSay(dialBuffer, 'dtmf');
    store.clearDialBuffer();

    store.setIsLoading(true);
    if (USE_MOCK) {
      // 목업: 01012345678 이면 환자 찾음
      await delay(800);
      if (dialBuffer === '01012345678') {
        store.setPatient('pat_mock01', '홍길동');
        store.setIsLoading(false);
        store.setPhase('SYMPTOM_COLLECT');
        await systemSay('홍길동 어르신, 어디가 불편하신가요? 우물정자를 누르고 말해주세요.');
      } else {
        store.setIsLoading(false);
        store.setPhase('IDENTIFY_BY_VOICE');
        await systemSay('등록된 번호를 찾을 수 없습니다. 이름과 생년월일을 말씀해주세요. 우물정자를 누르고 말해주세요.');
      }
    } else {
      try {
        const result = await intakeApi.identifyByPhone(store.sessionId!, dialBuffer);
        store.setPatient(result.patientId, result.name);
        await intakeApi.bindPatient(store.sessionId!, result.patientId);
        store.setIsLoading(false);
        store.setPhase('SYMPTOM_COLLECT');
        await systemSay(`${result.name} 어르신, 어디가 불편하신가요? 우물정자를 누르고 말해주세요.`);
      } catch {
        store.setIsLoading(false);
        store.setPhase('IDENTIFY_BY_VOICE');
        await systemSay('등록된 번호를 찾을 수 없습니다. 이름과 생년월일을 말씀해주세요. 우물정자를 누르고 말해주세요.');
      }
    }
  }, [store, userSay, systemSay]);

  // ─── 음성 녹음 시작/종료 ───
  const handleMicStart = useCallback(async () => {
    store.setIsRecording(true);
    await startRecording();
  }, [store, startRecording]);

  const handleMicStop = useCallback(async () => {
    const blob = await stopRecording();
    store.setIsRecording(false);
    userSay('(음성 입력)', 'voice');

    const { phase, sessionId } = useIntakeStore.getState();
    store.setIsLoading(true);

    if (USE_MOCK) {
      await delay(1200);
      if (phase === 'IDENTIFY_BY_VOICE') {
        store.setPatient('pat_mock02', '김영희');
        store.setIsLoading(false);
        store.setPhase('SYMPTOM_COLLECT');
        await systemSay('김영희 어르신, 어디가 불편하신가요? 우물정자를 누르고 말해주세요.');
      } else if (phase === 'SYMPTOM_COLLECT') {
        store.setSymptomText('머리가 아프고 어지러워요');
        store.setIsLoading(false);
        store.setPhase('RECOMMEND_DOCTOR');
        await systemSay('증상을 확인했습니다. 의사를 추천해드리겠습니다.');
        await delay(600);
        store.setAvailableSlots([
          { slotId: 'slot_01', doctorId: 'doc_01', doctorName: '김의사', date: '2026-03-12', startTime: '10:00', endTime: '10:30' },
          { slotId: 'slot_02', doctorId: 'doc_02', doctorName: '박의사', date: '2026-03-12', startTime: '14:00', endTime: '14:30' },
        ]);
        store.setCurrentSlotIndex(0);
        store.setPhase('SLOT_SELECT');
        await systemSay('내과 김의사 선생님, 3월 12일 오전 10시 진료가 가능합니다. 예약하시겠습니까? 네이면 1번, 다른 시간은 2번을 눌러주세요.');
      }
    } else {
      try {
        if (phase === 'IDENTIFY_BY_VOICE') {
          const voiceResult = await intakeApi.submitVoiceTurn(sessionId!, blob, '이름과 생년월일을 말씀해주세요');
          // 서버에서 이름/생년월일 파싱 후 식별
          store.setIsLoading(false);
          if (voiceResult.exceptionCode) {
            store.incrementRetry();
            await systemSay(voiceResult.ttsMessage);
          } else {
            store.setPhase('SYMPTOM_COLLECT');
            await systemSay(voiceResult.ttsMessage);
          }
        } else if (phase === 'SYMPTOM_COLLECT') {
          const voiceResult = await intakeApi.submitVoiceTurn(sessionId!, blob, '어디가 불편하신가요?');
          store.setIsLoading(false);

          if (voiceResult.exceptionCode) {
            store.incrementRetry();
            await systemSay(voiceResult.ttsMessage);
            return;
          }

          store.setSymptomText(voiceResult.sttText);
          store.setPhase('RECOMMEND_DOCTOR');
          await systemSay(voiceResult.ttsMessage);

          store.setIsLoading(true);
          const rec = await intakeApi.recommendDoctor(sessionId!, voiceResult.sttText);
          store.setIsLoading(false);
          store.setAvailableSlots(rec.availableSlots);
          store.setCurrentSlotIndex(0);

          if (rec.availableSlots.length > 0) {
            const slot = rec.availableSlots[0];
            store.setPhase('SLOT_SELECT');
            await systemSay(
              `${rec.departmentName} ${slot.doctorName} 선생님, ${slot.date} ${slot.startTime} 진료가 가능합니다. 예약하시겠습니까? 네이면 1번, 다른 시간은 2번을 눌러주세요.`,
            );
          } else {
            await systemSay('현재 가능한 예약 시간이 없습니다. 다시 전화해주세요.');
            store.setPhase('SESSION_END');
          }
        }
      } catch {
        store.setIsLoading(false);
        await systemSay('처리 중 오류가 발생했습니다. 다시 시도해주세요.');
      }
    }
  }, [store, stopRecording, userSay, systemSay]);

  useEffect(() => {
    submitDialBufferRef.current = submitDialBuffer;
    micToggleRef.current = isRecording ? handleMicStop : handleMicStart;
  }, [submitDialBuffer, handleMicStart, handleMicStop, isRecording]);

  // ─── 신규 예약 흐름 ───
  async function handleNewBookingFlow() {
    store.setPhase('IDENTIFY_BY_INPUT');
    store.clearDialBuffer();
    await systemSay('등록된 전화번호를 입력해주세요. 입력 후 우물정자를 눌러주세요.');
  }

  // ─── 기존 예약 흐름 ───
  async function handleExistingBookingFlow() {
    store.setPhase('IDENTIFY_BY_INPUT');
    store.clearDialBuffer();
    await systemSay('등록된 전화번호를 입력해주세요. 입력 후 우물정자를 눌러주세요.');
  }

  // ─── 슬롯 선택 ───
  async function handleSlotSelection(digit: string) {
    const { availableSlots, currentSlotIndex, sessionId } = useIntakeStore.getState();

    if (digit === '1') {
      const slot = availableSlots[currentSlotIndex];
      store.setIsLoading(true);

      if (USE_MOCK) {
        await delay(800);
        store.setIsLoading(false);
        store.setPhase('BOOKING_CONFIRMED');
        await systemSay(`${slot.date} ${slot.startTime} ${slot.doctorName} 선생님 예약이 확정되었습니다. 감사합니다.`);
        store.setPhase('SESSION_END');
        store.addMessage({ role: 'system', text: '통화가 종료되었습니다.', type: 'info' });
      } else {
        try {
          const booking = await intakeApi.createBooking(sessionId!, slot.slotId);
          store.setIsLoading(false);
          store.setPhase('BOOKING_CONFIRMED');
          await systemSay(`${booking.appointmentDate} ${booking.startTime} ${booking.doctorName} 선생님 예약이 확정되었습니다. 감사합니다.`);
          store.setPhase('SESSION_END');
        } catch {
          store.setIsLoading(false);
          await systemSay('예약 처리 중 오류가 발생했습니다.');
        }
      }
    } else if (digit === '2') {
      const nextIdx = currentSlotIndex + 1;
      if (nextIdx < availableSlots.length) {
        store.setCurrentSlotIndex(nextIdx);
        const slot = availableSlots[nextIdx];
        await systemSay(
          `${slot.doctorName} 선생님, ${slot.date} ${slot.startTime} 진료가 가능합니다. 예약하시겠습니까? 1번 예약, 2번 다른 시간.`,
        );
      } else {
        await systemSay('더 이상 가능한 시간이 없습니다. 다시 전화해주세요. 감사합니다.');
        store.setPhase('SESSION_END');
        store.addMessage({ role: 'system', text: '통화가 종료되었습니다.', type: 'info' });
      }
    }
  }

  // ─── 기존 예약 조회/취소 ───
  async function handleBookingAction(digit: string) {
    const { existingBookings } = useIntakeStore.getState();

    if (digit === '1' && existingBookings.length > 0) {
      const b = existingBookings[0];
      await systemSay(`${b.appointmentDate} ${b.startTime} ${b.doctorName} 선생님 ${b.departmentName} 예약이 있습니다.`);
      store.setPhase('SESSION_END');
      store.addMessage({ role: 'system', text: '통화가 종료되었습니다.', type: 'info' });
    } else if (digit === '2' && existingBookings.length > 0) {
      const b = existingBookings[0];
      store.setIsLoading(true);
      if (USE_MOCK) {
        await delay(600);
        store.setIsLoading(false);
        await systemSay('예약이 취소되었습니다. 감사합니다.');
      } else {
        try {
          await intakeApi.cancelBooking(b.bookingId);
          store.setIsLoading(false);
          await systemSay('예약이 취소되었습니다. 감사합니다.');
        } catch {
          store.setIsLoading(false);
          await systemSay('취소 처리 중 오류가 발생했습니다.');
        }
      }
      store.setPhase('SESSION_END');
      store.addMessage({ role: 'system', text: '통화가 종료되었습니다.', type: 'info' });
    }
  }

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

function delay(ms: number) {
  return new Promise((r) => setTimeout(r, ms));
}
