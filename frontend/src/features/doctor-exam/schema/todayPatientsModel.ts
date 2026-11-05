import type { TodayPatient, TodayStatus } from "../types/doctorExam";

// Lọc và đếm danh sách "Bệnh nhân hôm nay" (UC35). Danh sách không có thứ tự hàng đợi và
// không có chức năng gọi bệnh nhân (R3 UC35 postcondition); chỉ sắp theo giờ.

export type TodayFilter = "ALL" | TodayStatus;

export const TODAY_FILTERS: readonly TodayFilter[] = [
  "ALL",
  "WAITING_FOR_EXAMINATION",
  "IN_EXAMINATION",
  "AWAITING_CONCLUSION",
  "COMPLETED",
  "NOT_ARRIVED",
];

export function countByStatus(
  patients: readonly TodayPatient[],
): Record<TodayFilter, number> {
  const counts = Object.fromEntries(
    TODAY_FILTERS.map((filter) => [filter, 0]),
  ) as Record<TodayFilter, number>;
  counts.ALL = patients.length;
  for (const patient of patients)
    if (patient.status in counts) counts[patient.status] += 1;
  return counts;
}

function fold(text: string): string {
  return text
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/đ/g, "d")
    .toLowerCase();
}

export function filterPatients(
  patients: readonly TodayPatient[],
  filter: TodayFilter,
  query: string,
): TodayPatient[] {
  const needle = fold(query.trim());
  return patients
    .filter((patient) => filter === "ALL" || patient.status === filter)
    .filter(
      (patient) =>
        !needle ||
        fold(`${patient.patientName} ${patient.patientCode ?? ""}`).includes(
          needle,
        ),
    )
    .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));
}

/** Chỉ mở được lượt khám đã có mã (lịch hẹn đã check-in hoặc khách vãng lai). */
export function canOpenVisit(patient: TodayPatient): boolean {
  return patient.visitId !== null;
}
