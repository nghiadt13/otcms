import type { AppointmentStatus } from "@/lib/domain/clinicVisit";
import { mockFail } from "@/lib/api/mockServer";
import { findMockPatient } from "@/lib/mock/sharedPatientDirectoryMock";
import type { CheckInPatient, ExamFee } from "../types/checkIn";
import { SEED_APPOINTMENTS, type SeedAppointment } from "./checkInMockAppointments";
import { MOCK_TODAY, SEED_OPEN_VISITS, SEED_PATIENTS, type OpenVisitSeed } from "./checkInMockData";
import { findQueueEntryByAppointment, isExamFeePaid } from "./receptionMockStore";

// Trạng thái suy ra cho lớp API giả: trạng thái lịch là giá trị gốc, hoặc Đã tiếp nhận nếu kho có
// bản ghi hàng đợi; phí là giá trị gốc, hoặc Đã thanh toán nếu kho ghi nhận khoản thu. Hàm tra cứu
// ném ApiError (404 MSG-41) khi không tìm thấy.

export const isToday = (startAt: string) => startAt.slice(0, 10) === MOCK_TODAY;

export const byStartTime = (a: { startAt: string }, b: { startAt: string }) =>
  a.startAt.localeCompare(b.startAt);

/** Chuỗi giờ Việt Nam +07:00 (ví dụ 2026-10-24T08:12:00+07:00) từ một thời điểm. */
export function toVietnamIso(date: Date): string {
  const local = new Date(date.getTime() + 7 * 3600_000);
  return `${local.toISOString().slice(0, 19)}+07:00`;
}

/** Bệnh nhân của kho tiếp nhận; hồ sơ tạo ở UC10 được tra trong kho giả dùng chung. */
export function patientOrFail(id: string): CheckInPatient {
  return (
    SEED_PATIENTS.find((item) => item.id === id) ??
    findMockPatient(id) ??
    mockFail(404, "MSG-41")
  );
}

export function appointmentOrFail(id: string): SeedAppointment {
  return SEED_APPOINTMENTS.find((item) => item.id === id) ?? mockFail(404, "MSG-41");
}

export function openVisitOf(patientId: string): OpenVisitSeed | undefined {
  return SEED_OPEN_VISITS[patientId];
}

export function statusOf(seed: SeedAppointment): AppointmentStatus {
  return findQueueEntryByAppointment(seed.id) ? "CHECKED_IN" : seed.status;
}

export function feeOf(seed: SeedAppointment): ExamFee {
  return isExamFeePaid(seed.id) ? { ...seed.fee, status: "PAID" } : { ...seed.fee };
}

function hasCheckedInToday(patientId: string): boolean {
  return SEED_APPOINTMENTS.some(
    (seed) => seed.patientId === patientId && isToday(seed.startAt) && statusOf(seed) === "CHECKED_IN",
  );
}

/** Vãng lai khi không có lượt khám đang mở và chưa tiếp nhận hôm nay. */
export function canWalkIn(patientId: string): boolean {
  return openVisitOf(patientId) === undefined && !hasCheckedInToday(patientId);
}
