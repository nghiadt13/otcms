import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";

/** BE trả mã MSG ở error.code của response; apiRequest đọc vào errorCode và ApiError.code. */
export function medicalHistoryErrorMessage(error: unknown): string {
  if (isApiError(error)) {
    switch (error.code) {
      case "MSG-20":
        return formatAppMessage("MSG-20");
      case "MSG-19":
        return formatAppMessage("MSG-19");
      case "MSG-16":
        return formatAppMessage("MSG-16");
    }
    if (error.status === 400) return formatAppMessage("MSG-20");
    if (error.status === 403 || error.status === 404)
      return formatAppMessage("MSG-19");
  }
  return messageFromError(error);
}
