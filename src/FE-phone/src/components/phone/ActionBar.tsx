import './ActionBar.css';

interface ActionBarProps {
  phase: string;
  isRecording: boolean;
  onCall: () => void;
  onHangUp: () => void;
  onMicStart: () => void;
  onMicStop: () => void;
  onSend: () => void;
  dialBuffer: string;
  showMic: boolean;
  showSend: boolean;
}

export default function ActionBar({
  phase,
  isRecording,
  onCall,
  onHangUp,
  onMicStart,
  onMicStop,
  onSend,
  dialBuffer,
  showMic,
  showSend,
}: ActionBarProps) {
  const isIdle = phase === 'IDLE';
  const isEnded = phase === 'SESSION_END';

  return (
    <div className="action-bar">
      <div className="action-btn-wrapper">
        <button
          className="action-btn action-btn--call"
          onClick={onCall}
          disabled={!isIdle && !isEnded}
          title="전화 걸기"
        >
          <svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
            <path d="M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z" />
          </svg>
        </button>
      </div>

      <div className="action-btn-wrapper">
        {showMic && (
          <button
            className={`action-btn action-btn--mic ${isRecording ? 'action-btn--recording' : ''}`}
            onClick={isRecording ? onMicStop : onMicStart}
            title={isRecording ? '녹음 중지' : '음성 입력'}
          >
            {isRecording ? '⏹' : '🎤'}
          </button>
        )}
        {showSend && dialBuffer && (
          <button className="action-btn action-btn--send" onClick={onSend} title="입력 전송">
            ✉
          </button>
        )}
      </div>

      <div className="action-btn-wrapper">
        <button
          className="action-btn action-btn--hangup"
          onClick={onHangUp}
          disabled={isIdle || isEnded}
          title="전화 끊기"
        >
          <svg viewBox="0 0 24 24" fill="currentColor" width="28" height="28">
            <path d="M12 9c-1.6 0-3.15.25-4.6.72v3.1c0 .39-.23.74-.56.9-.98.49-1.87 1.12-2.66 1.85-.18.18-.43.28-.7.28-.28 0-.53-.11-.71-.29L.29 13.08c-.18-.17-.29-.42-.29-.7 0-.28.11-.53.29-.71C3.34 8.78 7.46 7 12 7s8.66 1.78 11.71 4.67c.18.18.29.43.29.71 0 .28-.11.52-.29.71l-2.48 2.48c-.18.18-.43.29-.71.29-.27 0-.52-.11-.7-.28-.79-.74-1.69-1.36-2.67-1.85-.33-.16-.56-.5-.56-.9v-3.1C15.15 9.25 13.6 9 12 9z" />
          </svg>
        </button>
      </div>
    </div>
  );
}
