import { apiRequest } from "@/lib/api/client";
import { todayIsoDate } from "@/lib/format/formatDate";
import type { VisitContext } from "../types/doctorExam";

// Lượt đang có bước khám của bác sĩ: `GET /api/v1/doctor/visits/active` (BE commit cbf955f). Chỉ gồm
// lượt OPEN có bước chăm sóc IN_PROGRESS của chính bác sĩ, kèm danh tính tối thiểu của bệnh
// nhân (họ tên, ngày sinh, giới; không có số điện thoại, địa chỉ: BR-13). Hàng chờ (A6) không trả
// ngày sinh/giới, nên tuổi/giới của ngữ cảnh lượt chỉ lấy được từ đây, khi lượt đang có bước khám.

export type ActiveVisitDto = {
  visitId: string;
  patientProfileId: string;
  patient: {
    fullName: string;
    dateOfBirth: string | null;
    gender: string | null;
  };
  visitSource: string;
  linkedAppointmentId: string | null;
  assignmentId: string;
  stepCode: string;
  startedAt: string;
  expectedEnd: string | null;
};

/** Lỗi hoặc BE chưa có endpoint thì trả danh sách rỗng: ngữ cảnh lượt chỉ thiếu tuổi/giới. */
export async function fetchActiveVisits(
  signal?: AbortSignal,
): Promise<ActiveVisitDto[]> {
  try {
    const result = await apiRequest<{ items?: ActiveVisitDto[] }>(
      "/api/v1/doctor/visits/active",
      { signal },
    );
    return result.items ?? [];
  } catch (error) {
    if (signal?.aborted) throw error;
    return [];
  }
}

/** Tuổi tròn tính theo ngày hôm nay ở Việt Nam; null nếu thiếu hoặc sai ngày sinh. */
export function ageFromBirthDate(
  birthDate: string | null,
  today: string = todayIsoDate(),
): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(birthDate ?? "");
  if (!match) return null;
  const [, y, m, d] = match.map(Number) as [number, number, number, number];
  const [ty, tm, td] = today.split("-").map(Number);
  const age = ty - y - (tm < m || (tm === m && td < d) ? 1 : 0);
  return age >= 0 && age < 150 ? age : null;
}

export function sexFromGender(gender: string | null): VisitContext["sex"] {
  if (!gender) return null;
  const value = gender.toUpperCase();
  if (value === "MALE") return "Nam";
  if (value === "FEMALE") return "Nữ";
  return "Khác";
}

export function sourceOfVisit(visitSource: string): "BOOKED" | "WALK_IN" {
  return visitSource === "WALK_IN" ? "WALK_IN" : "BOOKED";
}
