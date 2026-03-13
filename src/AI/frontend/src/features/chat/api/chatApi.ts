import type {
  CreateSessionResponse,
  MessageHistoryResponse,
  SubmitMessageRequest,
  SubmitMessageResponse
} from "../model/chat.types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {})
    },
    ...init
  });

  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || `요청 처리에 실패했습니다. (${response.status})`);
  }

  return response.json() as Promise<T>;
}

export function createSession(locale = "ko-KR") {
  return request<CreateSessionResponse>("/api/v1/chat/sessions", {
    method: "POST",
    body: JSON.stringify({ locale })
  });
}

export function getMessages(sessionId: string) {
  return request<MessageHistoryResponse>(`/api/v1/chat/sessions/${sessionId}/messages`);
}

export function submitMessage(sessionId: string, payload: SubmitMessageRequest) {
  return request<SubmitMessageResponse>(`/api/v1/chat/sessions/${sessionId}/messages`, {
    method: "POST",
    body: JSON.stringify(payload)
  });
}
