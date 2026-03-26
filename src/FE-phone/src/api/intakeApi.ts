import api from './axiosInstance';
import type {
  CancelBookingResult,
  IdentifyResult,
  RecommendationResult,
  BookingResult,
} from '../types/intake';

interface IdentifyPatientApiResponse {
  identified: boolean;
  patient: {
    patientId: string;
    name: string;
    birthDate6: string;
    regionCode: string;
  } | null;
}

interface ExistingBookingsApiResponse {
  bookings: BookingResult[];
  totalCount: number;
}

interface CreateBookingApiResponse {
  bookingId: string;
  status: string;
  caseId: string;
  doctor: {
    doctorId: string;
    name: string;
    department: string;
    departmentName: string;
  };
  appointmentDate: string;
  startTime: string;
  endTime: string;
  ttsMessage: string;
}

function mapIdentifyResponse(data: IdentifyPatientApiResponse): IdentifyResult | null {
  if (!data.identified || !data.patient) {
    return null;
  }

  return {
    patientId: data.patient.patientId,
    name: data.patient.name,
    birthDate6: data.patient.birthDate6,
    regionCode: data.patient.regionCode,
  };
}

function mapBookingResponse(data: CreateBookingApiResponse): BookingResult {
  return {
    bookingId: data.bookingId,
    status: data.status,
    caseId: data.caseId,
    appointmentDate: data.appointmentDate,
    startTime: data.startTime,
    endTime: data.endTime,
    doctorName: data.doctor.name,
    departmentName: data.doctor.departmentName,
    ttsMessage: data.ttsMessage,
  };
}

export async function createSession(
  callerNumber: string,
): Promise<{ intakeSessionId: string }> {
  const { data } = await api.post('/intake/sessions', {
    callerNumber,
    channel: 'WEB_SIMULATOR',
  });
  return data;
}

export async function identifyByCallerNumber(
  sessionId: string,
  callerNumber: string,
): Promise<IdentifyResult | null> {
  const { data } = await api.post<IdentifyPatientApiResponse>(
    `/intake/sessions/${sessionId}/identify/by-caller-number`,
    { callerNumber },
  );
  return mapIdentifyResponse(data);
}

export async function identifyByPhone(
  sessionId: string,
  phone: string,
): Promise<IdentifyResult | null> {
  const { data } = await api.post<IdentifyPatientApiResponse>(
    `/intake/sessions/${sessionId}/identify/by-phone`,
    { phone },
  );
  return mapIdentifyResponse(data);
}

export async function bindPatient(
  sessionId: string,
  patientId: string,
): Promise<void> {
  await api.patch(`/intake/sessions/${sessionId}`, { patientId });
}

export async function completeSession(
  sessionId: string,
  completionReason: string,
): Promise<void> {
  await api.patch(`/intake/sessions/${sessionId}`, {
    status: 'COMPLETED',
    completionReason,
  });
}

export async function recommendDoctor(
  sessionId: string,
  departmentCode: string,
): Promise<RecommendationResult> {
  const { data } = await api.post(
    `/intake/sessions/${sessionId}/recommend`,
    { departmentCode },
  );
  return data;
}

export async function createBooking(
  sessionId: string,
  slotId: string,
): Promise<BookingResult> {
  const { data } = await api.post<CreateBookingApiResponse>(
    `/intake/sessions/${sessionId}/bookings`,
    { slotId },
  );
  return mapBookingResponse(data);
}

export async function getExistingBookings(
  sessionId: string,
): Promise<BookingResult[]> {
  const { data } = await api.get<ExistingBookingsApiResponse>(
    `/intake/sessions/${sessionId}/bookings`,
  );
  return data.bookings;
}

export async function cancelBooking(
  sessionId: string,
  bookingId: string,
): Promise<CancelBookingResult> {
  const { data } = await api.post<CancelBookingResult>(
    `/intake/sessions/${sessionId}/bookings/${bookingId}/cancel`,
  );
  return data;
}
