import { CARE_RELATIONSHIP_LABELS } from "@/lib/domain/careRelationship";
import {
  EXAM_FEE_STATUS_LABELS,
  VISIT_TYPE_LABELS,
} from "@/lib/domain/clinicVisit";
import { formatCurrencyVnd } from "@/lib/format/formatCurrencyVnd";
import { formatDate, formatTime } from "@/lib/format/formatDate";
import { PATIENT_ROUTES } from "@/lib/routes/patientRoutes";
import { dateOfStart, longWeekdayLabel } from "../../schema/bookingTime";
import type { PatientAppointment } from "../../types/patientAppointment";

// Chữ hiển thị của lịch hẹn khu bệnh nhân. Không có luật nghiệp vụ: chỉ đổi dữ liệu BE thành chữ.

type Patient = PatientAppointment["patient"];
type ExamFee = PatientAppointment["examFee"];

/** "Thứ năm, 23/10/2026 · 08:30" */
export function appointmentWhenLabel(startAt: string): string {
  const date = dateOfStart(startAt);
  return `${longWeekdayLabel(date)}, ${formatDate(date)} · ${formatTime(startAt)}`;
}

/** Tiêu đề trang chi tiết: "Lịch khám ngày 23/10/2026". */
export const appointmentDetailTitle = (startAt: string): string =>
  `Lịch khám ngày ${formatDate(dateOfStart(startAt))}`;

/** "Nguyễn Văn An · Bản thân": luôn ghi người khám và quan hệ. */
export const patientOfLabel = (patient: Patient): string =>
  `${patient.fullName} · ${CARE_RELATIONSHIP_LABELS[patient.relationship]}`;

/** "Phí khám 200.000 ₫ · Chưa thanh toán" */
export const examFeeLabel = (examFee: ExamFee): string =>
  `Phí khám ${formatCurrencyVnd(examFee.amount)} · ${EXAM_FEE_STATUS_LABELS[examFee.status]}`;

export const visitTypeLabel = (isFollowUp: boolean): string =>
  isFollowUp ? VISIT_TYPE_LABELS.FOLLOW_UP : VISIT_TYPE_LABELS.FIRST_VISIT;

/** "08:30 thứ năm 23/10/2026": giờ và ngày trong câu xác nhận hủy. */
export function appointmentSentenceTime(startAt: string): string {
  const date = dateOfStart(startAt);
  return `${formatTime(startAt)} ${longWeekdayLabel(date).toLowerCase()} ${formatDate(date)}`;
}

/** Câu tiền khi hủy (BR-53): chỉ hiện khi phí đã trả trực tuyến. */
export const refundNotice = (examFee: ExamFee): string =>
  `Phí khám ${formatCurrencyVnd(examFee.amount)} đã thanh toán trực tuyến sẽ không được hoàn lại.`;

/** Câu chỉ dẫn khi đến quầy, cho lịch còn Đã đặt. Có mã BN thì ghi kèm. */
export const arrivalNote = (patientCode: string | undefined): string =>
  patientCode
    ? `Khi đến phòng khám, báo mã bệnh nhân ${patientCode} hoặc số điện thoại và ngày sinh ở quầy lễ tân.`
    : "Khi đến phòng khám, báo số điện thoại và ngày sinh của người khám ở quầy lễ tân.";

/** Đường tới tiến độ lượt khám (phase 05) khi lịch đã có lượt khám. */
export const visitHrefOf = (visitId: string | null): string | null =>
  visitId ? PATIENT_ROUTES.visitDetail(visitId) : null;
