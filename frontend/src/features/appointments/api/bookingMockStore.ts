import { clone } from "@/lib/api/mockServer";
import {
  findMockAppointmentFee,
  markMockAppointmentCancelled,
  resetMockAppointmentFees,
  upsertMockAppointmentFee,
} from "@/lib/mock/sharedAppointmentFeeMock";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import type { DoctorRef } from "../types/appointment";
import type { StoredAppointment } from "../types/booking";
import { BOOKABLE_SERVICES } from "./bookingMockCatalog";
import { createSeedAppointments } from "./bookingMockSeed";
import { patientOwnSeeds } from "./patientAppointmentMockData";

// KHO GIẢ DÙNG CHUNG của lịch hẹn trong feature `appointments` (một tiến trình trình duyệt).
//
// - Ghi: `bookingApi.createAppointment` thêm lịch mới vào đây; giờ đó biến khỏi `getOpenTimes`.
//   Lễ tân chuyển bác sĩ (`changeStoredAppointmentDoctor`) cũng ghi vào đây: khu bệnh nhân thấy
//   bác sĩ mới ngay, không có kho riêng của khu Lễ tân.
// - Đọc: danh sách "Lịch hẹn" của khu bệnh nhân (phase 04) và danh sách lịch theo ngày của khu
//   Lễ tân (S02) đọc bằng `readStoredAppointments()`, rồi tự lọc theo phạm vi của mình. Không
//   giữ bản sao riêng để hai khu luôn thấy cùng một lịch.
// - Phí khám: mỗi lịch của hồ sơ đã gắn có một dòng trong kho phí dùng chung
//   (`lib/mock/sharedAppointmentFeeMock.ts`) để khu thanh toán thấy lịch này. Trạng thái phí đọc
//   từ kho đó (thanh toán đổi ở đó), không từ bản ghi lúc đặt.
// - Đồng hồ: `bookingMockNow()` là giờ "máy chủ" của kho giả. Test cố định bằng
//   `resetBookingMock({ now })`.
// - Kho chỉ là dữ liệu: không kiểm quyền, không luật đặt. Mọi lớp API trả ra ngoài phải chiếu
//   bớt trường nội bộ (SĐT, mã BN, người đặt) theo đúng hợp đồng của mình.

let clock: (() => Date) | null = null;
let records: StoredAppointment[] = createSeedAppointments(
  new Date(),
  getLinkedProfileRecords(),
);
let takenStarts = new Set<string>();
let sequence = 9000;
publishSeedFees();

/** Giờ máy chủ của kho giả. Không có đồng hồ cố định thì dùng giờ thật. */
export function bookingMockNow(): Date {
  return clock ? clock() : new Date();
}

/** Chỉ dùng trong test và demo: đặt lại kho về dữ liệu ban đầu. `now` cố định đồng hồ. */
export function resetBookingMock(options: { now?: Date } = {}): void {
  const fixed = options.now;
  clock = fixed ? () => new Date(fixed.getTime()) : null;
  records = createSeedAppointments(bookingMockNow(), getLinkedProfileRecords());
  takenStarts = new Set();
  sequence = 9000;
  publishSeedFees();
}

/**
 * Bản sao toàn bộ lịch trong kho, sắp theo giờ bắt đầu, có trạng thái phí lấy từ kho phí dùng
 * chung. Sửa bản sao không đổi kho.
 */
export function readStoredAppointments(): StoredAppointment[] {
  return clone(records)
    .map(withFeeStatus)
    .sort((a, b) => a.startAt.localeCompare(b.startAt) || a.id.localeCompare(b.id));
}

/** Thêm một lịch mới vào kho (chỉ lớp đặt lịch gọi). */
export function appendStoredAppointment(record: StoredAppointment): void {
  records.push(clone(record));
  publishFee(record, getLinkedProfileRecords());
}

/**
 * Đổi một lịch thành Đã hủy để giờ đó được nhả (chỉ lớp hủy lịch của khu bệnh nhân gọi). Kho phí
 * dùng chung ghi lịch đã hủy nên khoản chưa trả không còn được thanh toán; phí đã trả giữ nguyên.
 */
export function cancelStoredAppointment(id: string): void {
  records = records.map((record): StoredAppointment =>
    record.id === id ? { ...record, status: "CANCELLED" } : record,
  );
  markMockAppointmentCancelled(id);
}

/**
 * Đổi bác sĩ và phòng của một lịch (chỉ lớp chuyển bác sĩ của Lễ tân gọi; giờ giữ nguyên). Ghi vào
 * kho chung nên khu bệnh nhân thấy bác sĩ mới ngay, và bác sĩ mới bận giờ đó (`isDoctorBusyAt`).
 */
export function changeStoredAppointmentDoctor(
  id: string,
  change: { doctor: DoctorRef; roomName: string },
): void {
  records = records.map((record): StoredAppointment =>
    record.id === id
      ? { ...record, doctor: { ...change.doctor }, roomName: change.roomName }
      : record,
  );
}

/** Mã lịch kế tiếp: "apt-9001", "apt-9002"… (ổn định cho test). */
export function nextAppointmentId(): string {
  sequence += 1;
  return `apt-${sequence}`;
}

/** Bác sĩ đã có lịch còn hiệu lực (Đã đặt hoặc Đã tiếp nhận) đúng giờ này. */
export function isDoctorBusyAt(doctorId: string, startAt: string): boolean {
  return records.some(
    (record) =>
      record.doctor.id === doctorId &&
      record.startAt === startAt &&
      (record.status === "BOOKED" || record.status === "CHECKED_IN"),
  );
}

/** Người này đã có lịch Đã đặt đúng giờ này (trùng lịch của bệnh nhân). */
export function hasBookedPatientAt(patientId: string, startAt: string): boolean {
  return records.some(
    (record) =>
      record.patient.id === patientId &&
      record.startAt === startAt &&
      record.status === "BOOKED",
  );
}

/** Giờ đã bị người khác giữ trước (kịch bản MSG-30): không còn trong danh sách giờ trống. */
export function isStartTaken(startAt: string): boolean {
  return takenStarts.has(startAt);
}

export function markStartTaken(startAt: string): void {
  takenStarts.add(startAt);
}

/** Trạng thái phí lấy từ kho phí dùng chung (thanh toán đổi ở đó); không có dòng thì giữ bản ghi. */
function withFeeStatus(record: StoredAppointment): StoredAppointment {
  const fee = findMockAppointmentFee(record.id);
  return fee
    ? { ...record, examFee: { ...record.examFee, status: fee.examFeeStatus } }
    : record;
}

/**
 * Đưa phí của một lịch vào kho phí dùng chung. Chỉ lịch của hồ sơ đã gắn: thanh toán chỉ thấy các
 * hồ sơ đó (BR-19), còn người do lễ tân quản lý thì không.
 */
function publishFee(record: StoredAppointment, linked: readonly LinkedProfile[]): void {
  const profile = linked.find((item) => item.id === record.patient.id);
  if (!profile) return;
  upsertMockAppointmentFee({
    appointmentId: record.id,
    profileId: profile.id,
    patientName: record.patient.fullName,
    relationship: profile.relationship,
    startAt: record.startAt,
    serviceName: record.service.name,
    examFeeAmount: record.examFee.amount,
    examFeeStatus: record.examFee.status,
    appointmentStatus: record.status,
  });
}

/** Phí của lịch riêng khu bệnh nhân (không nằm trong kho đặt lịch) để thanh toán thấy cùng dữ liệu. */
function publishOwnFees(now: Date, linked: readonly LinkedProfile[]): void {
  for (const seed of patientOwnSeeds(now)) {
    const profile = linked.find((item) => item.patientCode === seed.patientCode);
    const service = BOOKABLE_SERVICES.find((item) => item.id === seed.serviceId);
    if (!profile || !service) continue;
    upsertMockAppointmentFee({
      appointmentId: seed.id,
      profileId: profile.id,
      patientName: profile.fullName,
      relationship: profile.relationship,
      startAt: seed.startAt,
      serviceName: service.name,
      examFeeAmount: service.price,
      examFeeStatus: seed.feeStatus,
      appointmentStatus: seed.status,
    });
  }
}

/** Dựng lại kho phí dùng chung từ dữ liệu ban đầu. Gọi lúc nạp module và khi reset. */
function publishSeedFees(): void {
  const linked = getLinkedProfileRecords();
  resetMockAppointmentFees();
  for (const record of records) publishFee(record, linked);
  publishOwnFees(bookingMockNow(), linked);
}
