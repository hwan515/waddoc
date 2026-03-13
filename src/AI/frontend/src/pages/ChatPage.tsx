import { useChatSession } from "../features/chat/hooks/useChatSession";
import { useRealtimeStt } from "../features/voice/hooks/useRealtimeStt";
import { useTts } from "../features/tts/hooks/useTts";

export function ChatPage() {
  const { isSpeaking, speak, stop } = useTts();
  const { sessionId, sttConnection, messages, isBooting, isSubmitting, error, sendVoiceTranscript } =
    useChatSession();
  const { liveTranscript, status, error: sttError, startListening, stopListening } =
    useRealtimeStt({
      connection: sttConnection,
      onFinalTranscript: async ({ turnId, text, durationMs }) => {
        const response = await sendVoiceTranscript(turnId, text, durationMs);
        if (response?.ttsText) {
          speak(response.ttsText);
        }
      }
    });

  const micBusy = status !== "idle";
  const micLabel =
    status === "connecting"
      ? "마이크 연결 중..."
      : status === "listening"
        ? "녹음 종료"
        : status === "finalizing"
          ? "음성 인식 마무리 중..."
          : "음성 입력 시작";
  const sttStatusLabel =
    status === "connecting"
      ? "연결 중"
      : status === "listening"
        ? "듣는 중"
      : status === "finalizing"
        ? "전사 확정 중"
        : "대기 중";

  async function handleMicButtonClick() {
    if (status === "listening") {
      await stopListening();
      return;
    }

    if (status !== "idle") {
      return;
    }

    speak("증상을 말씀해주세요.", {
      onEnd: () => {
        void startListening();
      },
      onError: () => {
        void startListening();
      }
    });
  }

  return (
    <main className="shell">
      <section className="hero">
        <h1>음성 기반 진료과 안내</h1>
        <p>
          마이크 입력은 AI 서버로 바로 전송하고, 최종 전사문만 Spring에 저장합니다.
          추천 진료과는 Upstage가 판단하고, 결과는 채팅과 음성으로 동시에 안내합니다.
        </p>
      </section>

      <section className="panel">
        <article className="chat-card">
          <div className="chat-log">
            {messages.map((message) => (
              <div
                className={`bubble ${message.role === "USER" ? "user" : "assistant"}`}
                key={message.id}
              >
                {message.text}
              </div>
            ))}

            {liveTranscript ? <div className="bubble live">{liveTranscript}</div> : null}
          </div>
        </article>

        <aside className="side-card">
          <h2>상태</h2>

          <div className="metric">
            <span className="label">세션 ID</span>
            <span className="value">{sessionId ?? "세션 준비 중..."}</span>
          </div>

          <div className="metric">
            <span className="label">STT 상태</span>
            <span className="value">{sttStatusLabel}</span>
          </div>

          <div className="metric">
            <span className="label">TTS 상태</span>
            <span className="value">{isSpeaking ? "재생 중" : "대기 중"}</span>
          </div>

          <div className="controls">
            <button
              className={`mic-button ${micBusy ? "secondary" : ""}`}
              disabled={
                isBooting ||
                isSubmitting ||
                !sttConnection ||
                status === "connecting" ||
                status === "finalizing"
              }
              onClick={() => void handleMicButtonClick()}
              type="button"
            >
              {micLabel}
            </button>

            <button className="mic-button secondary" onClick={stop} type="button">
              음성 읽기 중지
            </button>

            <span className={`status-pill ${isSubmitting ? "warn" : ""}`}>
              {isSubmitting ? "최종 전사문을 서버로 전송하는 중..." : "준비 완료"}
            </span>

            <div className="helper">
              partial transcript는 화면에만 표시되고, final transcript만 서버 저장 및
              추천 분석에 사용됩니다.
            </div>

            {error ? <div className="error">{error}</div> : null}
            {sttError ? <div className="error">{sttError}</div> : null}
          </div>
        </aside>
      </section>
    </main>
  );
}
