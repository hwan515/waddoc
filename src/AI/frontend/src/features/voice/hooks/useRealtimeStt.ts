import { useRef, useState } from "react";
import type { SttConnection } from "../../chat/model/chat.types";
import { connectSttSocket, type SttSocketClient } from "../api/sttSocket";
import { createAudioCapture, type AudioCaptureHandle } from "../lib/audioCapture";

interface FinalTranscriptPayload {
  turnId: string;
  text: string;
  durationMs: number;
}

interface UseRealtimeSttOptions {
  connection: SttConnection | null;
  onFinalTranscript: (payload: FinalTranscriptPayload) => Promise<void>;
}

export function useRealtimeStt(options: UseRealtimeSttOptions) {
  const socketRef = useRef<SttSocketClient | null>(null);
  const captureRef = useRef<AudioCaptureHandle | null>(null);
  const startedAtRef = useRef<number>(0);

  const [liveTranscript, setLiveTranscript] = useState("");
  const [status, setStatus] = useState<"idle" | "connecting" | "listening" | "finalizing">(
    "idle"
  );
  const [error, setError] = useState<string | null>(null);

  async function startListening() {
    if (!options.connection || status !== "idle") {
      return;
    }

    setError(null);
    setLiveTranscript("");
    setStatus("connecting");

    const turnId = `turn_${Date.now()}`;
    startedAtRef.current = Date.now();

    try {
      const socket = await connectSttSocket({
        baseUrl: options.connection.wsUrl,
        turnId,
        token: options.connection.token,
        language: "ko",
        sampleRate: options.connection.sampleRate,
        onEvent: async (event) => {
          if (event.type === "stt.partial") {
            setLiveTranscript(event.text);
          }

          if (event.type === "stt.final") {
            const durationMs = Date.now() - startedAtRef.current;
            setLiveTranscript("");
            setStatus("idle");
            if (captureRef.current) {
              await captureRef.current.stop();
              captureRef.current = null;
            }
            socket.disconnect();
            socketRef.current = null;
            await options.onFinalTranscript({
              turnId: event.turnId,
              text: event.text,
              durationMs
            });
          }

          if (event.type === "stt.error") {
            if (captureRef.current) {
              await captureRef.current.stop();
              captureRef.current = null;
            }
            socket.disconnect();
            socketRef.current = null;
            setError(event.message);
            setLiveTranscript("");
            setStatus("idle");
          }
        }
      });

      socketRef.current = socket;

      const capture = await createAudioCapture({
        targetSampleRate: options.connection.sampleRate,
        onChunk: (pcmChunk) => {
          socket.sendAudio(pcmChunk);
        }
      });

      captureRef.current = capture;
      setStatus("listening");
    } catch (startError) {
      if (captureRef.current) {
        await captureRef.current.stop();
        captureRef.current = null;
      }
      socketRef.current?.disconnect();
      socketRef.current = null;
      setStatus("idle");
      setError(startError instanceof Error ? startError.message : "음성 인식을 시작하지 못했습니다.");
    }
  }

  async function stopListening() {
    if (status !== "listening") {
      return;
    }

    setStatus("finalizing");

    if (captureRef.current) {
      await captureRef.current.stop();
      captureRef.current = null;
    }

    socketRef.current?.finishTurn();
  }

  return {
    liveTranscript,
    status,
    error,
    startListening,
    stopListening
  };
}
