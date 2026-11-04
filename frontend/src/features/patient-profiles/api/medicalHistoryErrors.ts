import { isApiError } from "@/lib/api/apiError";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";

/** Mã nghiệp vụ nằm ở error.code của response, được apiRequest đọc vào errorCode. */
export function medicalHistoryErrorMessage(error: unknown): string {
  if (isApiError(error)) {
    switch (error.errorCode) {
      case "VALIDATION_FAILED":
        return formatAppMessage("MSG-20");
      case "ACCESS_DENIED":
      case "PATIENT_PROFILE_NOT_FOUND":
        return formatAppMessage("MSG-19");
      case "STALE_VERSION":
        return formatAppMessage("MSG-16");
    }
    if (error.status === 400) return formatAppMessage("MSG-20");
    if (error.status === 403 || error.status === 404)
      return formatAppMessage("MSG-19");
  }
  return messageFromError(error);
}
