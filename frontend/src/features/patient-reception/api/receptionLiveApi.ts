import { apiRequest, publicApiRequest } from "@/lib/api/client";
import { toQueryString } from "@/lib/api/pagination";
export { getCurrentClinicArrivalProof } from "@/lib/api/clinicArrivalApi";

export type ReceptionReturnCandidate = {
  visitId: string;
  firstCheckInAt: string;
  returnDeadline: string;
  doctorProfileId: string;
  doctorName: string;
  roomId: string;
  roomLabel: string;
};

export function getReceptionReturnCandidate(patientProfileId: string, signal?: AbortSignal) {
  return apiRequest<ReceptionReturnCandidate | undefined>(
    `/api/v1/reception/patients/${encodeURIComponent(patientProfileId)}/return-candidate`,
    { signal },
  );
}

export function returnReceptionVisit(visitId: string) {
  return apiRequest<{ messageCode: string; data: { visitId: string; status: "WAITING"; queueNumber: number; doctorProfileId: string; doctorName: string; roomId: string; roomLabel: string } }>(
    `/api/v1/visits/${encodeURIComponent(visitId)}/returns`,
    { method: "POST" },
  );
}

export type ReceptionPatient = {
  patientProfileId: string;
  fullName: string;
  phoneCanonical: string;
  dateOfBirth: string;
};

export type ReceptionPatientDetail = ReceptionPatient & {
  phoneOwnerType: "PATIENT" | "GUARDIAN";
  gender: "MALE" | "FEMALE" | "OTHER";
  address: string | null;
  nationalId: string | null;
  identityDocumentType: "CCCD" | "PERSONAL_ID" | "PASSPORT" | null;
  version: number;
};
export type ReceptionPatientInput = {
  fullName: string; phone: string; phoneOwnerType: "PATIENT" | "GUARDIAN";
  dateOfBirth: string; gender: "MALE" | "FEMALE" | "OTHER";
  address: string; nationalId: string | null;
  identityDocumentType: "CCCD" | "PERSONAL_ID" | "PASSPORT" | null;
};
export function getReceptionPatient(id: string) {
  return apiRequest<ReceptionPatientDetail>(`/api/v1/patient-profiles/${encodeURIComponent(id)}`);
}
export function matchReceptionPatients(phone: string, dateOfBirth: string, fullName?: string) {
  const params = toQueryString({ phone, dateOfBirth, fullName });
  return apiRequest<{ items: ReceptionPatient[] }>(`/api/v1/patient-profiles/matches${params}`);
}
export function createPatientProfile(input: ReceptionPatientInput) {
  return apiRequest<ReceptionPatient>("/api/v1/patient-profiles", { method: "POST", body: JSON.stringify(input) });
}
export function updatePatientProfile(id: string, input: ReceptionPatientInput, version: number) {
  return apiRequest<ReceptionPatientDetail>(`/api/v1/patient-profiles/${encodeURIComponent(id)}`, {
    method: "PUT", body: JSON.stringify({ ...input, version }),
  });
}

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

export function getReceptionRescheduleAvailability(appointmentId: string, from: string, to: string, signal?: AbortSignal) {
  return apiRequest<{ slots: ReceptionAvailabilitySlot[] }>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}/reschedule-availability${toQueryString({ from, to })}`,
    { signal },
  );
}

export function rescheduleReceptionAppointment(input: { appointmentId: string; doctorId: string; startTime: string; reason: string }) {
  return apiRequest<ReceptionAppointment>(`/api/v1/appointments/${encodeURIComponent(input.appointmentId)}/reschedule`, {
    method: "PATCH", body: JSON.stringify({ doctorId: input.doctorId, startTime: input.startTime, reason: input.reason.trim() }),
  });
}

export function cancelReceptionAppointment(input: { appointmentId: string; reason: string; clinicFault: boolean }) {
  return apiRequest<ReceptionAppointment>(`/api/v1/appointments/${encodeURIComponent(input.appointmentId)}/cancel`, {
    method: "POST", body: JSON.stringify({ reason: input.reason.trim(), clinicFault: input.clinicFault }),
  });
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

/** Kết quả check-in (A7): lượt đã vào hàng chờ với số thứ tự của bác sĩ và phòng của ca. */
export type ReceptionCheckIn = {
  appointmentId: string;
  visitId: string;
  checkedInAt: string;
  queueNumber: number;
  doctorName: string;
  roomLabel: string | null;
};

export function checkInAppointment(input: {
  appointmentId: string;
  verifiedPatientProfileId: string;
  idempotencyKey: string;
}) {
  return apiRequest<ReceptionCheckIn>(
    `/api/v1/appointments/${input.appointmentId}/check-in`,
    {
      method: "POST",
      headers: { "Idempotency-Key": input.idempotencyKey },
      body: JSON.stringify({
        verifiedPatientProfileId: input.verifiedPatientProfileId,
      }),
    },
  );
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

export async function listLaboratoryRooms(orderId: string, signal?: AbortSignal): Promise<ImagingQueueRoom[]> {
  const result = await apiRequest<{ items: { id: string; code: string; label: string }[] }>(
    `/api/v1/reception/clinical-service-orders/${encodeURIComponent(orderId)}/eligible-laboratory-rooms`, { signal },
  );
  return result.items.map((room) => ({ roomId: room.id, roomCode: room.code, displayLabel: room.label }));
}

export async function dispatchLaboratoryOrder(orderId: string, roomId: string): Promise<void> {
  await apiRequest(`/api/v1/reception/clinical-service-orders/${encodeURIComponent(orderId)}/laboratory-dispatch`, {
    method: "POST", body: JSON.stringify({ roomId }),
  });
}
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

// Hàng chờ theo phòng (UC-32, UC-33). Thứ tự hàng do BE quyết (sort_order); FE chỉ hiển thị.
// Lỗi nghiệp vụ đi qua ApiError và đổi sang mã MSG bằng messageFromError.

/** Khu hàng chờ: phòng khám (EXAM) hoặc phòng dịch vụ (SERVICE, UC-46). */
export type RoomQueueStage = "EXAM" | "SERVICE";

/** Một lượt trong hàng của phòng (BE: QueueRoomDtos.Entry). `status`: WAITING, CALLED, IN_EXAMINATION, ABSENT, AWAITING_CONCLUSION... */
export type RoomQueueEntry = {
  id: string;
  visitId: string;
  queueNumber: number | null;
  stage: RoomQueueStage;
  status: string;
  sortOrder: number;
};

/** Kết quả của thao tác gọi, vắng, đưa lại (BE: QueueRoomDtos.Updated). */
export type RoomQueueUpdate = { messageCode: string; entry: RoomQueueEntry };

/** Hàng của một phòng hôm nay theo thứ tự hàng. */
export function listRoomQueue(
  roomId: string,
  stage: RoomQueueStage,
  signal?: AbortSignal,
) {
  const params = toQueryString({ stage });
  return apiRequest<RoomQueueEntry[]>(
    `/api/v1/queue/rooms/${encodeURIComponent(roomId)}${params}`,
    { signal },
  );
}

/** Gọi người kế tiếp của phòng. BE trả MSG-42 khi đang có người được gọi hoặc đang khám. */
export function callNextInRoom(roomId: string, stage: RoomQueueStage) {
  const params = toQueryString({ stage });
  return apiRequest<RoomQueueUpdate>(
    `/api/v1/queue/rooms/${encodeURIComponent(roomId)}/call-next${params}`,
    { method: "POST" },
  );
}

/** Đánh dấu vắng cho người đang được gọi (CALLED). */
export function markQueueEntryAbsent(entryId: string) {
  return apiRequest<RoomQueueUpdate>(
    `/api/v1/queue/entries/${encodeURIComponent(entryId)}/absence`,
    { method: "POST" },
  );
}

/**
 * Đưa người vắng (trong ngày) quay lại hàng. `beforeEntryId` là người đang chờ đứng sau để chèn
 * trước; null nghĩa là cuối hàng. Lý do bắt buộc (BE trả MSG-39 khi rỗng).
 */
export function returnQueueEntry(
  entryId: string,
  input: { beforeEntryId: string | null; reason: string },
) {
  return apiRequest<RoomQueueUpdate>(
    `/api/v1/queue/entries/${encodeURIComponent(entryId)}/return`,
    {
      method: "POST",
      body: JSON.stringify({
        beforeEntryId: input.beforeEntryId,
        reason: input.reason.trim(),
      }),
    },
  );
}

/** Số đang gọi của phòng cho màn hình công khai (chỉ có số, không có tên bệnh nhân). */
export function getRoomNowServing(roomId: string, signal?: AbortSignal) {
  return publicApiRequest<number[]>(
    `/api/v1/public/queue/rooms/${encodeURIComponent(roomId)}/now-serving`,
    { signal },
  );
}
