import { apiRequest } from "@/lib/api/client";
import { toQueryString } from "@/lib/api/pagination";
export { getCurrentClinicArrivalProof } from "@/lib/api/clinicArrivalApi";

export type ReceptionPatient = {
  patientProfileId: string;
  fullName: string;
  phoneCanonical: string;
  dateOfBirth: string;
};

export type ReceptionAppointment = {
  id: string;
  patientProfileId: string;
  patientName: string | null;
  serviceId: string;
  serviceName: string | null;
  servicePrice: number | null;
  doctorId: string | null;
  doctorDisplayName: string | null;
  status: string;
  purpose?: string | null;
  startTime: string;
  visitId: string | null;
  examFeePaid: boolean | null;
};

export type ReceptionAppointmentPage = {
  items: ReceptionAppointment[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export type ReceptionAvailabilitySlot = {
  serviceId: string;
  doctorId: string;
  doctorDisplayName: string;
  startTime: string;
  endTime: string;
  occupiedUntil: string;
};

export type ReceptionArrival = {
  appointmentId: string;
  patientProfileId: string;
  source: string;
  arrivedAt: string;
  state: string;
  recordedAt: string;
};

export type ReceptionArrivalWorklist = {
  date: string;
  page: number;
  size: number;
  hasMore: boolean;
  items: ReceptionArrival[];
};

export type ReceptionWalkIn = {
  visitId: string;
  patientProfileId: string;
  priorAppointmentId: string | null;
  arrivedAt: string;
};

export type ReceptionQueue = {
  date: string;
  page: number;
  size: number;
  hasMore: boolean;
  items: {
    queueEntryId: string;
    visitId: string;
    arrivedAt: string;
    status: string;
    position: number;
    currentStage: string | null;
    currentRoom: string | null;
  }[];
};

export function searchReceptionPatients(query: string, signal?: AbortSignal) {
  const params = toQueryString({ query: query.trim(), page: 0, size: 20 });
  return apiRequest<{ content: ReceptionPatient[] }>(
    `/api/v1/patient-profiles${params}`,
    { signal },
  ).then((page) => page.content);
}

export function getReceptionVerificationProfile(
  patientProfileId: string,
  signal?: AbortSignal,
) {
  return apiRequest<{
    patientProfileId: string;
    fullName: string;
    dateOfBirth: string;
  }>(`/api/v1/patient-profiles/${patientProfileId}/reception-verification`, {
    signal,
  });
}

export function listReceptionAppointments(
  query: {
    from?: string;
    to?: string;
    patientProfileId?: string;
    status?: string;
    page?: number;
    size?: number;
  },
  signal?: AbortSignal,
) {
  const params = toQueryString({
    ...query,
    page: query.page ?? 0,
    size: query.size ?? 100,
  });
  return apiRequest<ReceptionAppointmentPage>(`/api/v1/appointments${params}`, {
    signal,
  });
}

export function getReceptionAppointment(id: string, signal?: AbortSignal) {
  return apiRequest<ReceptionAppointment>(`/api/v1/appointments/${id}`, {
    signal,
  });
}

export function getStaffAppointmentAvailability(
  query: { serviceId: string; from: string; to: string; doctorId?: string },
  signal?: AbortSignal,
) {
  const params = toQueryString(query);
  return apiRequest<{ slots: ReceptionAvailabilitySlot[] }>(
    `/api/v1/staff/appointments/availability${params}`,
    { signal },
  );
}

export function getAppointmentReassignmentAvailability(
  appointmentId: string,
  from: string,
  to: string,
  signal?: AbortSignal,
) {
  const params = toQueryString({ from, to });
  return apiRequest<{ slots: ReceptionAvailabilitySlot[] }>(
    `/api/v1/appointments/${appointmentId}/reassignment-availability${params}`,
    { signal },
  );
}

export function createReceptionAppointment(input: {
  patientProfileId: string;
  serviceId: string;
  doctorId: string;
  startTime: string;
  purpose: string;
}) {
  return apiRequest<ReceptionAppointment>("/api/v1/appointments", {
    method: "POST",
    body: JSON.stringify(input),
  });
}

export function reassignReceptionAppointment(input: {
  appointmentId: string;
  doctorId: string;
  reason: string;
}) {
  return apiRequest<ReceptionAppointment>(
    `/api/v1/appointments/${input.appointmentId}/reassign`,
    {
      method: "POST",
      body: JSON.stringify({
        doctorId: input.doctorId,
        reason: input.reason.trim(),
      }),
    },
  );
}

export function listReceptionArrivals(
  query: { date: string; state?: string },
  signal?: AbortSignal,
) {
  const params = toQueryString({ ...query, page: 0, size: 100 });
  return apiRequest<ReceptionArrivalWorklist>(
    `/api/v1/appointment-arrivals${params}`,
    { signal },
  );
}

export function recordManualReceptionArrival(input: {
  appointmentId: string;
  reason: string;
}) {
  return apiRequest<ReceptionArrival>(
    `/api/v1/appointments/${input.appointmentId}/arrivals/manual`,
    {
      method: "POST",
      body: JSON.stringify({ reason: input.reason.trim() }),
    },
  );
}

export function listWalkInReceptionQueue(date: string, signal?: AbortSignal) {
  const params = toQueryString({ date, page: 0, size: 100 });
  return apiRequest<ReceptionQueue>(`/api/v1/reception-queue${params}`, {
    signal,
  });
}

export function checkInAppointment(input: {
  appointmentId: string;
  verifiedPatientProfileId: string;
  idempotencyKey: string;
}) {
  return apiRequest<{
    appointmentId: string;
    visitId: string;
    checkedInAt: string;
  }>(`/api/v1/appointments/${input.appointmentId}/check-in`, {
    method: "POST",
    headers: { "Idempotency-Key": input.idempotencyKey },
    body: JSON.stringify({
      verifiedPatientProfileId: input.verifiedPatientProfileId,
    }),
  });
}

export function createReceptionWalkIn(input: {
  patientProfileId: string;
  priorAppointmentId?: string;
  idempotencyKey: string;
}) {
  return apiRequest<ReceptionWalkIn>("/api/v1/visits/walk-ins", {
    method: "POST",
    headers: { "Idempotency-Key": input.idempotencyKey },
    body: JSON.stringify({
      patientProfileId: input.patientProfileId,
      priorAppointmentId: input.priorAppointmentId,
    }),
  });
}

export type ClinicalRoundOrder = {
  orderId: string;
  orderCode: string;
  serviceName: string;
  serviceType: string;
  priceSnapshot: number;
  orderStatus: string;
  paymentStatus: "PENDING_PAYMENT" | "PAID";
  diagnostic: {
    requestId: string;
    status: string;
    version: number;
    roomId: string | null;
    roomCode: string | null;
    displayLabel: string | null;
  } | null;
};
export type ClinicalOrderingRound = {
  roundId: string;
  visitId: string;
  patientProfileId: string;
  patientName: string | null;
  roundNumber: number;
  orderedAt: string;
  orders: ClinicalRoundOrder[];
};
export function listClinicalOrderingRounds(page = 0, signal?: AbortSignal) {
  return apiRequest<{ items: ClinicalOrderingRound[]; hasMore: boolean }>(
    `/api/v1/reception/clinical-ordering-rounds?page=${page}&size=20`,
    { signal },
  );
}
export type ImagingQueueRoom = {
  roomId: string;
  roomCode: string;
  displayLabel: string;
};
export type ImagingRoomAssignment = { serviceOrderId: string; roomId: string };
export async function listClinicalImagingRoomOptions(
  serviceOrderIds: string[],
  signal?: AbortSignal,
) {
  const items: { serviceOrderId: string; items: ImagingQueueRoom[] }[] = [];
  for (let offset = 0; offset < serviceOrderIds.length; offset += 200) {
    const ids = serviceOrderIds
      .slice(offset, offset + 200)
      .map(encodeURIComponent)
      .join(",");
    const result = await apiRequest<{ items: typeof items }>(
      `/api/v1/reception/clinical-service-orders/eligible-imaging-rooms?serviceOrderIds=${ids}`,
      { signal },
    );
    items.push(...result.items);
  }
  return items;
}
export function dispatchClinicalImagingRound(
  roundId: string,
  assignments: ImagingRoomAssignment[],
) {
  return apiRequest<{ diagnosticRequestIds: string[] }>(
    `/api/v1/reception/clinical-ordering-rounds/${encodeURIComponent(roundId)}/imaging-dispatch`,
    { method: "POST", body: JSON.stringify({ assignments }) },
  );
}
