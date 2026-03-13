export type MessageRole = "USER" | "ASSISTANT";
export type InputType = "voice" | "text";

export interface ChatMessage {
  id: string;
  role: MessageRole;
  text: string;
}

export interface SttConnection {
  wsUrl: string;
  token: string;
  sampleRate: number;
  chunkMs: number;
  encoding: string;
}

export interface CreateSessionResponse {
  sessionId: string;
  stt: SttConnection;
}

export interface SttMeta {
  engine: string;
  language: string;
  durationMs: number;
}

export interface SubmitMessageRequest {
  turnId: string;
  inputType: InputType;
  text: string;
  sttMeta?: SttMeta;
}

export interface Recommendation {
  departmentCode: string;
  departmentName: string;
  confidence: number;
  reason: string;
}

export interface SubmitMessageResponse {
  sessionId: string;
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
  recommendation: Recommendation;
  ttsText: string;
}

export interface MessageHistoryResponse {
  sessionId: string;
  messages: ChatMessage[];
}

export type SttServerEvent =
  | { type: "stt.ready" }
  | { type: "stt.partial"; turnId: string; text: string }
  | { type: "stt.final"; turnId: string; text: string; confidence: number }
  | { type: "stt.error"; code: string; message: string };

