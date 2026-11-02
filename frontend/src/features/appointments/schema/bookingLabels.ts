import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDate, formatTime } from "@/lib/format/formatDate";
import type { BookableService, BookingPatient } from "../types/booking";
import { longWeekdayLabel } from "./bookingTime";

// Nhãn hiển thị của luồng đặt lịch. Luôn ghi "của ai" (tên + quan hệ) ở chỗ người dùng xác nhận.

/** "Nguyễn Văn An · Bản thân"; khu Lễ tân không có quan hệ thì chỉ tên. */
export function patientLabelOf(patient: BookingPatient): string {
  return patient.relationship
    ? `${patient.fullName} · ${CARE_RELATIONSHIP_LABELS[patient.relationship]}`
    : patient.fullName;
}

/** Dòng tóm tắt khi khối dịch vụ đã thu gọn: "Khám cột sống · 250.000 ₫". */
export const serviceSummaryOf = (service: BookableService): string =>
  `${service.name} · ${formatCurrencyVnd(service.price)}`;

/** "Thứ năm 23/10/2026". */
export const dateSummaryOf = (date: string): string =>
  `${longWeekdayLabel(date)} ${formatDate(date)}`;

/** "08:30" (giờ VN), dùng khi khối giờ đã thu gọn. */
export const timeSummaryOf = (startAt: string): string => formatTime(startAt);
