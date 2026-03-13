import { useEffect, useState } from "react";
import { createSession, getMessages, submitMessage } from "../api/chatApi";
import type { ChatMessage, SttConnection } from "../model/chat.types";

export function useChatSession() {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [sttConnection, setSttConnection] = useState<SttConnection | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isBooting, setIsBooting] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;

    async function boot() {
      try {
        const session = await createSession();
        if (!active) {
          return;
        }

        setSessionId(session.sessionId);
        setSttConnection(session.stt);

        const history = await getMessages(session.sessionId);
        if (!active) {
          return;
        }

        setMessages(history.messages);
      } catch (bootError) {
        if (!active) {
          return;
        }

        setError(bootError instanceof Error ? bootError.message : "세션을 생성하지 못했습니다.");
      } finally {
        if (active) {
          setIsBooting(false);
        }
      }
    }

    void boot();

    return () => {
      active = false;
    };
  }, []);

  async function sendVoiceTranscript(turnId: string, text: string, durationMs: number) {
    if (!sessionId) {
      throw new Error("Session is not ready.");
    }

    setIsSubmitting(true);
    setError(null);
    const pendingMessageId = `pending_${turnId}`;

    setMessages((current) => [
      ...current,
      {
        id: pendingMessageId,
        role: "USER",
        text
      }
    ]);

    try {
      const response = await submitMessage(sessionId, {
        turnId,
        inputType: "voice",
        text,
        sttMeta: {
          engine: "faster-whisper",
          language: "ko",
          durationMs
        }
      });

      setMessages((current) => [
        ...current.filter((message) => message.id !== pendingMessageId),
        response.userMessage,
        response.assistantMessage
      ]);

      return response;
    } catch (submitError) {
      setMessages((current) => current.filter((message) => message.id !== pendingMessageId));
      setError(submitError instanceof Error ? submitError.message : "전사문을 전송하지 못했습니다.");
      return null;
    } finally {
      setIsSubmitting(false);
    }
  }

  return {
    sessionId,
    sttConnection,
    messages,
    isBooting,
    isSubmitting,
    error,
    sendVoiceTranscript
  };
}
