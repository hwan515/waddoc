import { useIntakeStore } from '../../stores/intakeStore';
import { useIntakeFlow } from '../../hooks/useIntakeFlow';
import StatusBar from './StatusBar';
import ChatDisplay from './ChatDisplay';
import DialBufferDisplay from './DialBufferDisplay';
import DialPad from './DialPad';
import ActionBar from './ActionBar';
import './FeaturePhone.css';

export default function FeaturePhone() {
  const phase = useIntakeStore((s) => s.phase);
  const dialBuffer = useIntakeStore((s) => s.dialBuffer);
  const storeIsRecording = useIntakeStore((s) => s.isRecording);
  const isLoading = useIntakeStore((s) => s.isLoading);

  const {
    startCall,
    endCall,
    handleDigit,
    submitDialBuffer,
    handleMicStart,
    handleMicStop,
    isRecording,
  } = useIntakeFlow();

  const isActive = phase !== 'IDLE' && phase !== 'SESSION_END';
  const dialDisabled = !isActive || isLoading;

  // 마이크 버튼: 음성 입력이 필요한 단계에서만 표시
  const showMic =
    phase === 'IDENTIFY_BY_VOICE' ||
    phase === 'EXISTING_IDENTIFY_BY_VOICE' ||
    phase === 'SYMPTOM_COLLECT';

  // 전송 버튼: 번호 입력 모드에서 표시
  const showSend =
    phase === 'IDENTIFY_BY_INPUT' || phase === 'EXISTING_IDENTIFY';

  return (
    <div className="feature-phone">
      {/* 피처폰 외곽 */}
      <div className="phone-body">
        {/* 스피커 그릴 */}
        <div className="phone-speaker">
          <div className="speaker-slit" />
          <div className="speaker-slit" />
          <div className="speaker-slit" />
        </div>

        {/* 화면 영역: 상태바 + 대화 */}
        <div className="phone-screen">
          <StatusBar phase={phase} isActive={isActive} />
          <DialBufferDisplay buffer={dialBuffer} visible={showSend} />
          <ChatDisplay />
        </div>

        {/* 하단: 액션바 + 다이얼패드 */}
        <ActionBar
          phase={phase}
          isRecording={storeIsRecording || isRecording}
          onCall={startCall}
          onHangUp={endCall}
          onMicStart={handleMicStart}
          onMicStop={handleMicStop}
          onSend={submitDialBuffer}
          dialBuffer={dialBuffer}
          showMic={showMic}
          showSend={showSend}
        />
        <DialPad onDigit={handleDigit} disabled={dialDisabled} />

        {/* 하단 마이크 구멍 */}
        <div className="phone-mic-hole" />
      </div>
    </div>
  );
}
