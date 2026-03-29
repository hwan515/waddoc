import type { IntakePhase } from '../../types/intake';
import './StatusBar.css';

const PHASE_LABELS: Partial<Record<IntakePhase, string>> = {
  IDLE: '대기 중',
  GREETING: '연결 중...',
  MENU_SELECT: '예약 선택',
  IDENTIFY_AUTO: '환자 식별 중',
  IDENTIFY_BY_INPUT: '전화번호 입력',
  DEPARTMENT_SELECT: '진료과 선택',
  RECOMMEND_DOCTOR: '의사 매칭 중',
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
}

export default function StatusBar({ phase }: StatusBarProps) {
  return (
    <div className="status-bar">
      <div className="status-bar-left">
        <span className="status-label">{PHASE_LABELS[phase] ?? phase}</span>
      </div>
      <div className="status-bar-center">왔닥</div>
    </div>
  );
}
