import type { MsgCode } from "@/lib/api/apiError";

// Validator thuần cho dialog thêm/sửa ca. Trả mã MSG theo trường; rỗng = hợp lệ.
// Ngày/giờ sai dùng MSG-34, trường trống dùng MSG-12 (phase-04 câu 13).

export interface ShiftFormValues {
  staffId: string;
  date: string;
  startTime: string;
  endTime: string;
  roomId: string;
}

export type ShiftFormField = keyof ShiftFormValues;
export type ShiftFormErrors = Partial<Record<ShiftFormField, MsgCode>>;

const REQUIRED_TEXT: Record<ShiftFormField, string> = {
  staffId: "Chọn nhân viên.",
  date: "Chọn ngày.",
  startTime: "Nhập giờ bắt đầu.",
  endTime: "Nhập giờ kết thúc.",
  roomId: "Chọn phòng.",
};

const INVALID_TEXT: Record<ShiftFormField, string> = {
  staffId: "Nhân viên này không nhận được ca.",
  date: "Chọn ngày từ hôm nay trở đi.",
  startTime: "Giờ bắt đầu chưa hợp lệ.",
  endTime: "Giờ kết thúc phải sau giờ bắt đầu.",
  roomId: "Chọn phòng đang sẵn sàng.",
};

/** Câu hiển thị dưới ô cho một mã lỗi của trường (từ validator hoặc `fieldErrors` của BE). */
export function describeShiftFieldError(
  field: ShiftFormField,
  code: MsgCode,
): string {
  return code === "MSG-12" ? REQUIRED_TEXT[field] : INVALID_TEXT[field];
}

/** "HH:mm" -> phút trong ngày; sai định dạng -> NaN. */
export function timeToMinutes(value: string): number {
  const match = /^(\d{2}):(\d{2})$/.exec(value);
  if (!match) return Number.NaN;
  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  return hours > 23 || minutes > 59 ? Number.NaN : hours * 60 + minutes;
}

export function validateShiftForm(
  values: ShiftFormValues,
  today: string,
  options: { requireStaff: boolean },
): ShiftFormErrors {
  const errors: ShiftFormErrors = {};
  if (options.requireStaff && !values.staffId) errors.staffId = "MSG-12";
  if (!values.date) errors.date = "MSG-12";
  else if (values.date < today) errors.date = "MSG-34"; // cả khi thêm và khi sửa
  if (!values.startTime) errors.startTime = "MSG-12";
  if (!values.endTime) errors.endTime = "MSG-12";
  if (!values.roomId) errors.roomId = "MSG-12";

  if (!errors.startTime && !errors.endTime) {
    const start = timeToMinutes(values.startTime);
    const end = timeToMinutes(values.endTime);
    if (Number.isNaN(start)) errors.startTime = "MSG-34";
    if (Number.isNaN(end)) errors.endTime = "MSG-34";
    if (!errors.startTime && !errors.endTime && end <= start)
      errors.endTime = "MSG-34";
  }
  return errors;
}
