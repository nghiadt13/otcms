import { apiRequest } from "@/lib/api/client";
import type { PatientQrCheckIn } from "@/lib/api/clinicArrivalApi";

// Check-in lịch hẹn bằng mã QR của bệnh nhân (A7). Lib `clinicArrivalApi.recordPatientQrArrival` khai báo
// kiểu thiếu số thứ tự, bác sĩ và phòng mà BE trả sau khi cấp số; mở rộng kiểu tại đây, không sửa lib.

/** Kết quả check-in: lượt đã vào hàng chờ khám, kèm số thứ tự, bác sĩ và phòng. */
export interface PatientArrivalCheckInResult extends PatientQrCheckIn {
  queueNumber: number | null;
  doctorName: string | null;
  roomLabel: string | null;
}

/** POST /api/v1/appointments/{id}/arrivals. Lỗi ném ApiError, mã nghiệp vụ nằm ở `errorCode`. */
export function recordPatientArrivalCheckIn(
  appointmentId: string,
  proof: string,
): Promise<PatientArrivalCheckInResult> {
  return apiRequest<PatientArrivalCheckInResult>(
    `/api/v1/appointments/${encodeURIComponent(appointmentId)}/arrivals`,
    { method: "POST", body: JSON.stringify({ proof }) },
  );
}
