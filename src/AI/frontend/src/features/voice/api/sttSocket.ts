import type { SttServerEvent } from "../../chat/model/chat.types";

export interface SttSocketClient {
  sendAudio(chunk: Int16Array): void;
  finishTurn(): void;
  disconnect(): void;
}

interface ConnectOptions {
  baseUrl: string;
  turnId: string;
  token: string;
  language: string;
  sampleRate: number;
  onEvent: (event: SttServerEvent) => void;
}

export async function connectSttSocket(options: ConnectOptions): Promise<SttSocketClient> {
  const url = new URL(`${options.baseUrl.replace(/\/$/, "")}/${options.turnId}`);
  url.searchParams.set("token", options.token);

  return new Promise((resolve, reject) => {
    const socket = new WebSocket(url);
    socket.binaryType = "arraybuffer";

    socket.onopen = () => {
      socket.send(
        JSON.stringify({
          type: "stt.start",
          language: options.language,
          sampleRate: options.sampleRate
        })
      );

      resolve({
        sendAudio(chunk) {
          if (socket.readyState !== WebSocket.OPEN) {
            return;
          }

          socket.send(chunk.buffer);
        },
        finishTurn() {
          if (socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify({ type: "stt.stop" }));
          }
        },
        disconnect() {
          if (
            socket.readyState === WebSocket.OPEN ||
            socket.readyState === WebSocket.CONNECTING
          ) {
            socket.close();
          }
        }
      });
    };

    socket.onerror = () => {
      reject(new Error("실시간 STT 서버에 연결하지 못했습니다."));
    };

    socket.onmessage = (message) => {
      if (typeof message.data !== "string") {
        return;
      }

      options.onEvent(JSON.parse(message.data) as SttServerEvent);
    };
  });
}
