import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import type { VisitContext } from "../types/doctorExam";
import type { MedicalHistory, PatientAppointment } from "./doctorExamApi";

// Phần lịch hẹn và tiền sử của bệnh nhân trong bản live: tiền sử bệnh, lịch của bệnh nhân, đặt tái
// khám. Danh sách hôm nay, ngữ cảnh lượt và bắt đầu/kết thúc khám nằm ở doctorExamLiveQueue.ts.

/** Một lịch hẹn trong `GET /api/v1/appointments` (đủ trường cho danh sách và ngữ cảnh lượt). */
export type AppointmentDto = {
  id: string;
  patientProfileId: string;
  patientName: string | null;
  serviceName: string;
  doctorId: string;
  status: string;
  startTime: string;
  visitId: string | null;
};
export type AppointmentPage = {
  items: AppointmentDto[];
  page?: number;
  totalPages?: number;
};

export async function getMedicalHistory(
  patientProfileId: string,
  signal?: AbortSignal,
): Promise<MedicalHistory> {
  return apiRequest<MedicalHistory>(
    `/api/v1/patient-profiles/${encodeURIComponent(patientProfileId)}/medical-history`,
    { signal },
  );
}

export function saveMedicalHistory(
  patientProfileId: string,
  value: MedicalHistory,
) {
  return apiRequest<MedicalHistory>(
    `/api/v1/patient-profiles/${encodeURIComponent(patientProfileId)}/medical-history`,
    {
      method: "PUT",
      body: JSON.stringify({
        conditions: value.conditions,
        allergies: value.allergies,
        procedures: value.procedures,
        injuries: value.injuries,
        orthopedicHistory: value.orthopedicHistory,
        version: value.version,
      }),
    },
  );
}

export async function listPatientAppointments(
  patientProfileId: string,
  signal?: AbortSignal,
): Promise<PatientAppointment[]> {
  const page = await apiRequest<AppointmentPage>(
    `/api/v1/appointments?patientProfileId=${encodeURIComponent(patientProfileId)}&size=50`,
    { signal },
  );
  return page.items.map((item) => ({
    id: item.id,
    serviceName: item.serviceName,
    startTime: item.startTime,
    status: item.status,
  }));
}

export async function bookFollowUp(
  context: VisitContext,
  input: {
    serviceId: string;
    startTime: string;
    purpose: string;
    reason: string;
  },
): Promise<void> {
  if (!context.doctorProfileId) throw new ApiError(400, { code: "MSG-39" });
  await apiRequest<unknown>(
    `/api/v1/visits/${encodeURIComponent(context.visitId)}/follow-up-appointments`,
    {
      method: "POST",
      body: JSON.stringify({ ...input, doctorId: context.doctorProfileId }),
    },
  );
}
