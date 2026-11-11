import type { MsgCode } from "@/lib/api/apiError";

// Mã lỗi nghiệp vụ của backend (`error.code`) → mã MSG của R3 để hiển thị.
// Nằm ở module riêng, không trong appMessages.ts: ApiError (lib/api/apiError.ts) cần tra bảng khi
// dựng lỗi, còn appMessages.ts đã import apiError.ts; đặt chung sẽ thành vòng import.
// Thêm mã: chỉ thêm dòng vào cuối bảng; mã MSG đích phải có trong APP_MESSAGES (R3).
export const BACKEND_ERROR_MSG_CODES: Readonly<Record<string, MsgCode>> = {
  EXAM_FEE_UNPAID: "MSG-37",
  DOCTOR_NOT_ON_SHIFT: "MSG-04",
  VISIT_EXAM_STATE_CONFLICT: "MSG-42",
  VISIT_COMPLETION_NOT_ALLOWED: "MSG-42",
  VISIT_CAPACITY_NOT_AVAILABLE: "MSG-42",
  APPOINTMENT_CHANGE_TOO_LATE: "MSG-32",
  APPOINTMENT_NOT_CHANGEABLE: "MSG-32",
  SLOT_UNAVAILABLE: "MSG-30",
  CHECK_IN_TIME_NOT_ALLOWED: "MSG-37",
  ARRIVAL_PROOF_INVALID: "MSG-37",
};

const isMsgCode = (value: string): value is MsgCode => /^MSG-\d+$/.test(value);

/**
 * Mã MSG tương ứng với `error.code` của backend. Mã dạng `MSG-n` giữ nguyên; mã nghiệp vụ tra
 * bảng. Trả `undefined` khi không biết mã (`messageFromError` khi đó rơi về MSG-04).
 */
export function msgCodeFromBackendError(
  errorCode: string | undefined,
): MsgCode | undefined {
  if (errorCode === undefined) return undefined;
  if (isMsgCode(errorCode)) return errorCode;
  return Object.hasOwn(BACKEND_ERROR_MSG_CODES, errorCode)
    ? BACKEND_ERROR_MSG_CODES[errorCode]
    : undefined;
}
