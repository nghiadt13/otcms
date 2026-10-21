import { toast } from "sonner";
import type { MsgCode } from "@/lib/api/apiError";
import { APP_MESSAGES, formatAppMessage, messageFromError } from "./appMessages";

type Params = Record<string, string | number>;

// Mã MSG có trong bảng → dùng câu trong bảng; ngược lại coi là câu chữ tự do
// (dùng cho xác nhận thành công mà spec không gán MSG).
function resolve(codeOrText: string, params?: Params) {
  return codeOrText in APP_MESSAGES
    ? formatAppMessage(codeOrText as MsgCode, params)
    : codeOrText;
}

export function notifySuccess(codeOrText: string, params?: Params) {
  toast.success(resolve(codeOrText, params));
}

export function notifyWarning(codeOrText: string, params?: Params) {
  toast.warning(resolve(codeOrText, params));
}

/** `action` gắn nút vào toast, ví dụ MSG-36: "Xem lịch hẹn" (03), "Chuyển lịch hẹn" (04). */
export function notifyError(
  error: unknown,
  options: { action?: { label: string; onClick: () => void } } = {},
) {
  toast.error(messageFromError(error), {
    action: options.action,
    duration: options.action ? 10000 : undefined,
  });
}
