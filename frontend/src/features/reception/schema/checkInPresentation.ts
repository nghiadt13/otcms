import type { QueueType } from "@/lib/domain/clinicVisit";
import { VISIT_TYPE_LABELS } from "@/lib/domain/clinicVisit";
import { addDays, formatDate, formatTime } from "@/lib/format/formatDate";
import type {
  CheckInOption,
  CheckInResult,
  IneligibleAppointment,
} from "../types/checkIn";

// Chữ hiển thị của màn tiếp nhận (tuổi, số điện thoại, dòng lựa chọn, lý do không đủ điều kiện,
// tóm tắt phiếu số). Hàm thuần, không gọi API. Câu chữ không có mã UC/BR/MSG.

export const SEX_LABELS = { MALE: "Nam", FEMALE: "Nữ" } as const;

/** Tuổi tròn: so tháng và ngày (yyyy-MM-dd) để sinh nhật chưa tới thì chưa tính. */
export function ageInYears(dateOfBirth: string, today: string): number {
  const [birthYear, birthMonth, birthDay] = dateOfBirth.split("-").map(Number);
  const [year, month, day] = today.split("-").map(Number);
  let age = year - birthYear;
  if (month < birthMonth || (month === birthMonth && day < birthDay)) age -= 1;
  return age;
}

/** 0912345678 → "0912 345 678"; số khác 10 chữ số giữ nguyên. */
export function formatPhone(phone: string): string {
  return /^\d{10}$/.test(phone)
    ? `${phone.slice(0, 4)} ${phone.slice(4, 7)} ${phone.slice(7)}`
    : phone;
}

const NEXT_QUEUE_PHRASE: Record<Exclude<QueueType, "EXAMINATION">, string> = {
  SERVICE: "Vào hàng đợi dịch vụ",
  RESULT_REVIEW: "Vào hàng đợi đọc kết quả",
};

export const QUEUE_LINE_LABELS: Record<QueueType, string> = {
  EXAMINATION: "Hàng đợi khám",
  SERVICE: "Hàng đợi dịch vụ",
  RESULT_REVIEW: "Hàng đợi đọc kết quả",
};

export function optionTitle(option: CheckInOption): string {
  if (option.kind === "APPOINTMENT") {
    return `Lịch hẹn ${formatTime(option.startAt)} · ${option.serviceName} · ${VISIT_TYPE_LABELS[option.visitType]}`;
  }
  if (option.kind === "RETURN_VISIT") {
    return `Tiếp tục lượt khám ngày ${formatDate(option.firstCheckInDate)} · Ngày ${option.dayOfPeriod}/7`;
  }
  return "Khám vãng lai (không theo lịch)";
}

export function optionDetail(option: CheckInOption): string {
  if (option.kind === "APPOINTMENT") return `${option.doctorName} · ${option.roomName}`;
  if (option.kind === "RETURN_VISIT") {
    const { type, roomName, doctorName } = option.nextQueue;
    return `${NEXT_QUEUE_PHRASE[type]} · ${roomName} · ${doctorName}`;
  }
  return "Thu phí khám tại quầy";
}

/** "hôm nay" / "hôm qua" khi gần ngày hiện tại, còn lại ghi ngày dd/mm/yyyy. */
function dayPhrase(startAt: string, today: string): string {
  const day = startAt.slice(0, 10);
  if (day === today) return "hôm nay";
  if (day === addDays(today, -1)) return "hôm qua";
  return `ngày ${formatDate(startAt)}`;
}

/** Lý do một lịch không tiếp nhận được, viết cho lễ tân đọc. */
export function ineligibleMessage(item: IneligibleAppointment, today: string): string {
  const time = formatTime(item.startAt);
  const when = dayPhrase(item.startAt, today);
  switch (item.reason) {
    case "ALREADY_CHECKED_IN":
      return item.checkedIn
        ? `Lịch hẹn ${time} ${when} đã được tiếp nhận lúc ${formatTime(item.checkedIn.at)} · Số ${item.checkedIn.queueNumber} · ${item.checkedIn.roomName}.`
        : `Lịch hẹn ${time} ${when} đã được tiếp nhận.`;
    case "CANCELLED":
      return `Lịch hẹn ${time} ${when} đã bị hủy, không tiếp nhận được.`;
    case "NO_SHOW":
      return `Lịch hẹn ${time} ${when} đã quá giờ nên được ghi nhận là không đến.`;
    case "NOT_TODAY":
      return `Lịch hẹn ${time} ${when} chỉ tiếp nhận được trong ngày hẹn.`;
  }
}

export function peopleAheadText(count: number): string {
  return count === 0 ? "Chưa có ai đang chờ trước" : `Có ${count} người phía trước`;
}

/** Dòng dưới số thứ tự: loại hàng đợi · ưu tiên (nếu có) · số người phía trước. */
export function queueSummary(result: CheckInResult): string {
  const parts = [QUEUE_LINE_LABELS[result.queueType]];
  if (result.priorityLabel) parts.push(`Ưu tiên: ${result.priorityLabel}`);
  parts.push(peopleAheadText(result.peopleAhead));
  return parts.join(" · ");
}

/** Đếm ngược mm:ss cho khoản VNPay. */
export function formatCountdown(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}
