import api from './axiosInstance';
import type {
  IdentifyResult,
  VoiceTurnResult,
  RecommendationResult,
  BookingResult,
} from '../types/intake';

export async function createSession(): Promise<{ intakeSessionId: string }> {
  const { data } = await api.post('/intake/sessions');
  return data;
}

export async function identifyByPhone(
  sessionId: string,
  phone: string,
): Promise<IdentifyResult> {
  const { data } = await api.post(
    `/intake/sessions/${sessionId}/identify/by-phone`,
    { phone },
  );
  return data;
}

export async function identifyByInfo(
  sessionId: string,
  name: string,
  birthDate: string,
): Promise<IdentifyResult> {
  const { data } = await api.post(
    `/intake/sessions/${sessionId}/identify/by-info`,
    { name, birthDate },
  );
  return data;
}

export async function bindPatient(
  sessionId: string,
  patientId: string,
): Promise<void> {
  await api.patch(`/intake/sessions/${sessionId}/bind-patient`, { patientId });
}

export async function submitDtmfTurn(
  sessionId: string,
  dtmfInput: string,
): Promise<void> {
  await api.post(`/intake/sessions/${sessionId}/turns`, { dtmfInput });
}

export async function submitVoiceTurn(
  sessionId: string,
  audioBlob: Blob,
  prompt: string,
): Promise<VoiceTurnResult> {
  const formData = new FormData();
  formData.append('audioFile', audioBlob, 'recording.webm');
  formData.append('prompt', prompt);
  const { data } = await api.post(
    `/intake/sessions/${sessionId}/turns/voice`,
    formData,
    { headers: { 'Content-Type': 'multipart/form-data' } },
  );
  return data;
}

export async function recommendDoctor(
  sessionId: string,
  symptomText: string,
): Promise<RecommendationResult> {
  const { data } = await api.post(
    `/intake/sessions/${sessionId}/recommend`,
    { symptomText },
  );
  return data;
}

export async function createBooking(
  sessionId: string,
  slotId: string,
): Promise<BookingResult> {
  const { data } = await api.post(
    `/intake/sessions/${sessionId}/bookings`,
    { slotId },
  );
  return data;
}

export async function searchBookings(
  phone: string,
): Promise<BookingResult[]> {
  const { data } = await api.get('/bookings/search', { params: { phone } });
  return data;
}

export async function cancelBooking(bookingId: string): Promise<void> {
  await api.post(`/bookings/${bookingId}/cancel`);
}
