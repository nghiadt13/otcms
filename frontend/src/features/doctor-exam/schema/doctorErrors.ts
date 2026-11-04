import { ApiError, isApiError, type MsgCode } from "@/lib/api/apiError";
import { APP_MESSAGES, messageFromError } from "@/lib/messages/appMessages";
import { notifyError } from "@/lib/messages/notify";

// Đổi lỗi của BE sang mã MSG của R3 cho khu bác sĩ. Controller clinical trả mã MSG trong
// `error.code`, controller diagnostic trả mã kỹ thuật (STATE_CONFLICT...), còn lại chỉ có mã HTTP;
// bảng chung `messageFromError` chỉ đọc trường `code` nên nếu không đổi, mọi lỗi thành MSG-04.

const TECHNICAL_TO_MSG: Record<string, MsgCode> = {
  STATE_CONFLICT: "MSG-42",
  VERSION_CONFLICT: "MSG-42",
  IDEMPOTENCY_CONFLICT: "MSG-42",
  VISIT_CARE_NOT_CHANGEABLE: "MSG-42",
  VISIT_CARE_STEP_NOT_CONFIGURED: "MSG-42",
  EVIDENCE_MISMATCH: "MSG-47",
  REPORT_NOT_READY: "MSG-47",
  VALIDATION_FAILED: "MSG-39",
  ACCESS_DENIED: "MSG-40",
  UNAUTHORIZED_RECORD: "MSG-40",
  RECORD_NOT_FOUND: "MSG-41",
  RESOURCE_NOT_FOUND: "MSG-41",
  NOT_AVAILABLE: "MSG-41",
};

const STATUS_TO_MSG: Record<number, MsgCode> = {
  400: "MSG-39",
  403: "MSG-40",
  404: "MSG-41",
  409: "MSG-42",
};

function codeOf(error: ApiError): MsgCode | undefined {
  const raw = error.errorCode;
  if (raw && raw in APP_MESSAGES && raw.startsWith("MSG-"))
    return raw as MsgCode;
  return (raw && TECHNICAL_TO_MSG[raw]) || STATUS_TO_MSG[error.status];
}

/** Trả lỗi đã gắn mã MSG; lỗi không phải ApiError hoặc đã có mã thì giữ nguyên. */
export function normalizeDoctorError(error: unknown): unknown {
  if (!isApiError(error) || error.code) return error;
  const code = codeOf(error);
  return code
    ? new ApiError(error.status, {
        code,
        params: error.params,
        fieldErrors: error.fieldErrors,
      })
    : error;
}

const LOGIN_REQUIRED = "Bạn cần đăng nhập để tiếp tục.";

export function doctorErrorMessage(error: unknown): string {
  if (error instanceof Error && error.message === LOGIN_REQUIRED)
    return LOGIN_REQUIRED;
  return messageFromError(normalizeDoctorError(error));
}

export function notifyDoctorError(error: unknown): void {
  notifyError(normalizeDoctorError(error));
}
