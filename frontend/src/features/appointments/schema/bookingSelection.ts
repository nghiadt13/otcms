import type { LinkedProfile } from "@/lib/patient-scope/types";
import type {
  BookingPatient,
  OpenDay,
  OpenTime,
  OpenTimesResponse,
} from "../types/booking";
import { isBeforeServerNow, hhmmOfStart } from "./bookingTime";

// Trạng thái lựa chọn của luồng đặt lịch. Giá trị các khối là nguồn duy nhất; khối mở/đã chọn/ẩn
// suy ra từ giá trị (không có biến "đang sửa" riêng). "Đổi" một khối = xóa giá trị của khối đó
// và các khối sau (riêng giờ thì giữ bác sĩ, xem reducer).

/** Khối có thể mở lại bằng "Đổi". Bác sĩ không có "Đổi": bỏ trống là hợp lệ. */
export type BookingStepKey = "patient" | "service" | "date" | "time";

export interface BookingSelection {
  patientId: string | null;
  serviceId: string | null;
  /** YYYY-MM-DD */
  date: string | null;
  /** ISO +07:00 */
  startAt: string | null;
  /** null = phòng khám sắp xếp bác sĩ (UC21 N1.3). */
  doctorId: string | null;
}

export const EMPTY_BOOKING_SELECTION: BookingSelection = {
  patientId: null,
  serviceId: null,
  date: null,
  startAt: null,
  doctorId: null,
};

/** Hồ sơ đã gắn của tài khoản thành người khám của luồng đặt (khu bệnh nhân). */
export const toBookingPatient = (profile: LinkedProfile): BookingPatient => ({
  id: profile.id,
  fullName: profile.fullName,
  dateOfBirth: profile.dateOfBirth,
  relationship: profile.relationship,
});

/**
 * Người khám chọn sẵn (ví dụ từ `?profileId=`). Chỉ nhận mã có trong danh sách người khám; mã khác
 * (hoặc không có) thì `null` và người dùng tự chọn.
 */
export function preselectedPatientId(
  requested: string | undefined,
  patients: readonly Pick<BookingPatient, "id">[],
): string | null {
  return requested !== undefined && patients.some((patient) => patient.id === requested)
    ? requested
    : null;
}

/** Trạng thái đầu của luồng: chỉ người khám (nếu chọn sẵn) có giá trị, các khối khác để trống. */
export function initialBookingSelection(patientId: string | null): BookingSelection {
  return { ...EMPTY_BOOKING_SELECTION, patientId };
}

export type BookingSelectionAction =
  | { type: "selectPatient"; patientId: string }
  | { type: "selectService"; serviceId: string }
  | { type: "selectDate"; date: string }
  /** `doctorIdsAtTime`: bác sĩ còn trống ở giờ mới; giữ bác sĩ cũ nếu còn trong danh sách. */
  | { type: "selectTime"; startAt: string; doctorIdsAtTime: readonly string[] }
  | { type: "selectDoctor"; doctorId: string | null }
  | { type: "reopen"; step: BookingStepKey };

/** Xóa giá trị của `step` và các khối sau. Giờ không xóa bác sĩ (để giữ nếu còn ở giờ mới). */
function reopenStep(
  state: BookingSelection,
  step: BookingStepKey,
): BookingSelection {
  switch (step) {
    case "patient":
      return { ...EMPTY_BOOKING_SELECTION };
    case "service":
      return { ...state, serviceId: null, date: null, startAt: null, doctorId: null };
    case "date":
      return { ...state, date: null, startAt: null, doctorId: null };
    case "time":
      return { ...state, startAt: null };
  }
}

export function bookingSelectionReducer(
  state: BookingSelection,
  action: BookingSelectionAction,
): BookingSelection {
  switch (action.type) {
    case "selectPatient":
      return { ...EMPTY_BOOKING_SELECTION, patientId: action.patientId };
    case "selectService":
      return { ...state, serviceId: action.serviceId, date: null, startAt: null, doctorId: null };
    case "selectDate":
      return { ...state, date: action.date, startAt: null, doctorId: null };
    case "selectTime":
      return {
        ...state,
        startAt: action.startAt,
        doctorId:
          state.doctorId !== null && action.doctorIdsAtTime.includes(state.doctorId)
            ? state.doctorId
            : null,
      };
    case "selectDoctor":
      return { ...state, doctorId: action.doctorId };
    case "reopen":
      return reopenStep(state, action.step);
  }
}

export type StepState = "hidden" | "open" | "done";

export interface BookingStepStates {
  patient: StepState;
  service: StepState;
  date: StepState;
  time: StepState;
  /** Luôn "open" khi hiện: bác sĩ không bắt buộc, mặc định là phòng khám sắp xếp. */
  doctor: "hidden" | "open";
  /** Khối "Kiểm tra lại" hiện từ khi đã chọn dịch vụ; nút đặt chỉ bật khi đủ thông tin. */
  summary: boolean;
}

/**
 * Khối nào hiện, đang mở hay đã chọn. Khối chỉ hiện khi khối trước đã chọn. `hasPatientStep` là
 * true ở khu bệnh nhân (khối "Ai đi khám?"); khu Lễ tân đã cố định người khám từ ngoài.
 */
export function bookingStepStates(
  selection: BookingSelection,
  hasPatientStep: boolean,
): BookingStepStates {
  const patient: StepState = !hasPatientStep
    ? "hidden"
    : selection.patientId
      ? "done"
      : "open";
  const serviceVisible = !hasPatientStep || selection.patientId !== null;
  const service: StepState = !serviceVisible
    ? "hidden"
    : selection.serviceId
      ? "done"
      : "open";
  const date: StepState = !selection.serviceId
    ? "hidden"
    : selection.date
      ? "done"
      : "open";
  const time: StepState = !selection.date
    ? "hidden"
    : selection.startAt
      ? "done"
      : "open";
  return {
    patient,
    service,
    date,
    time,
    doctor: selection.startAt ? "open" : "hidden",
    summary: selection.serviceId !== null,
  };
}

/** Giờ buổi sáng là trước 12:00 (giờ VN); còn lại là buổi chiều. */
export function groupTimesByPeriod(times: readonly OpenTime[]): {
  morning: OpenTime[];
  afternoon: OpenTime[];
} {
  const morning: OpenTime[] = [];
  const afternoon: OpenTime[] = [];
  for (const time of times)
    (hhmmOfStart(time.startAt) < "12:00" ? morning : afternoon).push(time);
  return { morning, afternoon };
}

/** Bỏ giờ đã qua theo `serverNow` của BE; giữ đủ 14 ô ngày (ngày không còn giờ là "Hết giờ"). */
export function upcomingDays(response: OpenTimesResponse): OpenDay[] {
  return response.days.map((day) => ({
    date: day.date,
    times: day.times.filter(
      (time) => !isBeforeServerNow(time.startAt, response.serverNow),
    ),
  }));
}

/** Cả dải không còn giờ nào (kịch bản MSG-25). */
export function allDaysClosed(days: readonly OpenDay[]): boolean {
  return days.every((day) => day.times.length === 0);
}
