import { useEffect, useState } from 'react';
import type { IntakePhase } from '../../types/intake';
import './StatusBar.css';

const PHASE_LABELS: Partial<Record<IntakePhase, string>> = {
  IDLE: '대기 중',
  GREETING: '연결 중...',
  MENU_SELECT: '메뉴 선택',
  IDENTIFY_AUTO: '환자 식별 중',
  IDENTIFY_BY_INPUT: '전화번호 입력',
  IDENTIFY_BY_VOICE: '음성 식별',
  SYMPTOM_COLLECT: '증상 수집',
  RECOMMEND_DOCTOR: '진료과 추천',
  SLOT_SELECT: '예약 시간 선택',
  BOOKING_CONFIRMED: '예약 완료',
  EXISTING_IDENTIFY: '환자 식별',
  BOOKING_LOOKUP: '예약 조회',
  BOOKING_DETAIL: '예약 상세',
  BOOKING_CANCEL: '예약 취소',
  SESSION_END: '통화 종료',
};

interface StatusBarProps {
  phase: IntakePhase;
  isActive: boolean;
}

export default function StatusBar({ phase, isActive }: StatusBarProps) {
  const [elapsed, setElapsed] = useState(0);

  useEffect(() => {
    if (!isActive) {
      setElapsed(0);
      return;
    }
    const interval = setInterval(() => setElapsed((e) => e + 1), 1000);
    return () => clearInterval(interval);
  }, [isActive]);

  const minutes = String(Math.floor(elapsed / 60)).padStart(2, '0');
  const seconds = String(elapsed % 60).padStart(2, '0');

  return (
    <div className="status-bar">
      <div className="status-bar-left">
        <span className="status-label">{PHASE_LABELS[phase] ?? phase}</span>
      </div>
      <div className="status-bar-center">왔닥</div>
      <div className="status-bar-right">
        {isActive && (
          <span className="status-timer">{minutes}:{seconds}</span>
        )}
      </div>
    </div>
  );
}
