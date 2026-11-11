import type { TodayPatient, TodayStatus } from "../types/doctorExam";

// Lọc, đếm và sắp danh sách "Bệnh nhân hôm nay" (UC35, plan 261111-0208 mục 1). Thứ tự: đang khám,
// chờ kết luận, chờ khám (theo số thứ tự), chưa đến (theo giờ), đã xong. Không có nhóm ưu tiên và
// không có chức năng gọi bệnh nhân (UC31–33 nằm ngoài phạm vi).

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

/** Thứ tự nhóm trên màn hình: số nhỏ lên trước. */
const GROUP_ORDER: Record<TodayStatus, number> = {
  IN_EXAMINATION: 0,
  AWAITING_CONCLUSION: 1,
  WAITING_FOR_EXAMINATION: 2,
  NOT_ARRIVED: 3,
  COMPLETED: 4,
  INCOMPLETE: 4,
  CANCELLED_LEFT: 4,
};

/** Số thứ tự tăng dần; dòng chưa có số xếp sau. */
function byQueueNumber(a: TodayPatient, b: TodayPatient): number {
  if (a.queueNumber === b.queueNumber) return 0;
  if (a.queueNumber === null) return 1;
  if (b.queueNumber === null) return -1;
  return a.queueNumber - b.queueNumber;
}

/** Sắp theo nhóm trạng thái, rồi số thứ tự, rồi giờ. */
export function sortTodayPatients(
  patients: readonly TodayPatient[],
): TodayPatient[] {
  return [...patients].sort(
    (a, b) =>
      GROUP_ORDER[a.status] - GROUP_ORDER[b.status] ||
      byQueueNumber(a, b) ||
      (a.scheduledAt ?? "").localeCompare(b.scheduledAt ?? ""),
  );
}

export function filterPatients(
  patients: readonly TodayPatient[],
  filter: TodayFilter,
  query: string,
): TodayPatient[] {
  const needle = fold(query.trim());
  return sortTodayPatients(
    patients
      .filter((patient) => filter === "ALL" || patient.status === filter)
      .filter(
        (patient) =>
          !needle ||
          fold(`${patient.patientName} ${patient.patientCode ?? ""}`).includes(
            needle,
          ),
      ),
  );
}

/** Chỉ mở được lượt khám đã có mã (lịch hẹn đã check-in hoặc khách vãng lai). */
export function canOpenVisit(patient: TodayPatient): boolean {
  return patient.visitId !== null;
}
