import { useEffect, useRef } from 'react';
import { useIntakeStore } from '../../stores/intakeStore';
import type { ChatMessage } from '../../types/intake';
import './ChatDisplay.css';

function MessageBubble({ msg }: { msg: ChatMessage }) {
  const isSystem = msg.role === 'system';
  return (
    <div className={`chat-row ${isSystem ? 'chat-row--system' : 'chat-row--user'}`}>
      {isSystem && <div className="chat-avatar">&#128222;</div>}
      <div className={`chat-bubble ${isSystem ? 'chat-bubble--system' : 'chat-bubble--user'}`}>
        <p className="chat-text">{msg.text}</p>
        <span className="chat-time">
          {msg.timestamp.toLocaleTimeString('ko-KR', { hour: '2-digit', minute: '2-digit' })}
        </span>
      </div>
    </div>
  );
}

export default function ChatDisplay() {
  const messages = useIntakeStore((s) => s.messages);
  const isLoading = useIntakeStore((s) => s.isLoading);
  const isSpeaking = useIntakeStore((s) => s.isSpeaking);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isLoading]);

  return (
    <div className="chat-display">
      {messages.length === 0 && (
        <div className="chat-empty">
          <p className="chat-empty-title">&#128222; 왔닥 진료 예약</p>
        </div>
      )}
      {messages.map((msg) => (
        <MessageBubble key={msg.id} msg={msg} />
      ))}
      {isLoading && (
        <div className="chat-row chat-row--system">
          <div className="chat-avatar">&#128222;</div>
          <div className="chat-bubble chat-bubble--system chat-bubble--typing">
            <span className="dot" /><span className="dot" /><span className="dot" />
          </div>
        </div>
      )}
      {isSpeaking && (
        <div className="chat-speaking-indicator">&#128266; 음성 안내 중...</div>
      )}
      <div ref={bottomRef} />
    </div>
  );
}
