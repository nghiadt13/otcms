import { isApiError, type MsgCode } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";

// Lỗi check-in của khu bệnh nhân (quét QR, A7). Bảng chung (lib/messages/backendErrorMsgCodes) không có
// CHECK_IN_TIME_NOT_ALLOWED và ánh xạ APPOINTMENT_NOT_CHANGEABLE sang MSG-32 (lỗi hủy/đổi lịch), nên khu
// này ánh xạ riêng. Theo R3 (UC29 E2), lịch không đủ điều kiện check-in là MSG-37. `hint` là nhãn tĩnh chỉ
// bước tiếp theo, không phải thông báo mới.

export interface ArrivalFailure {
  message: string;
  hint: string | null;
}

const CHECK_IN_FAILURES: Readonly<
  Record<string, { code: MsgCode; hint: string }>
> = {
  CHECK_IN_TIME_NOT_ALLOWED: {
    code: "MSG-37",
    hint: "Nếu không đúng ngày hẹn, hãy nhờ lễ tân hỗ trợ tiếp nhận.",
  },
  APPOINTMENT_NOT_CHANGEABLE: {
    code: "MSG-37",
    hint: "Hãy nhờ lễ tân kiểm tra giúp.",
  },
  EXAM_FEE_UNPAID: {
    code: "MSG-94",
    hint: "Hãy ra quầy lễ tân để thanh toán phí khám, rồi quét lại mã.",
  },
  // R3 chưa có MSG riêng cho mã QR hỏng hoặc hết hạn: tạm dùng MSG-37 (cần nhóm xác nhận).
  ARRIVAL_PROOF_INVALID: {
    code: "MSG-37",
    hint: "Hãy quét mã mới đang hiển thị tại quầy.",
  },
};

/** Chữ lỗi và nhãn chỉ dẫn của một lần check-in thất bại; mã khác đi qua bảng MSG chung. */
export function arrivalFailureOf(cause: unknown): ArrivalFailure {
  const error = isApiError(cause) ? cause : undefined;
  const errorCode = error?.errorCode;
  if (error?.status === 409 && errorCode === "PATIENT_HAS_OPEN_VISIT") {
    return {
      message: "Bạn đã có lượt khám đang mở.",
      hint: "Vui lòng nhờ lễ tân hướng dẫn tiếp tục lượt khám hiện tại.",
    };
  }
  if (
    error?.fieldErrors?.some(
      ({ field, code }) => field === "proof" && code === "MSG-37",
    )
  ) {
    return {
      message: "Mã QR đã hết hạn.",
      hint: "Vui lòng quét mã mới đang hiển thị tại quầy lễ tân.",
    };
  }
  if (errorCode !== undefined && Object.hasOwn(CHECK_IN_FAILURES, errorCode)) {
    const failure = CHECK_IN_FAILURES[errorCode];
    return { message: formatAppMessage(failure.code), hint: failure.hint };
  }
  return { message: messageFromError(cause), hint: null };
}
