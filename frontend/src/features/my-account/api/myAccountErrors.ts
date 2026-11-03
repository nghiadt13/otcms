import { isApiError } from "@/lib/api/apiError";
import { AuthSessionError } from "@/lib/auth/authSession";
import { formatAppMessage, messageFromError } from "@/lib/messages/appMessages";

export function isMyAccountAccessError(error: unknown): boolean {
  return (
    (isApiError(error) && [401, 403, 404].includes(error.status)) ||
    (error instanceof AuthSessionError && [401, 403].includes(error.status)) ||
    (error instanceof Error &&
      error.message === "Bạn cần đăng nhập để tiếp tục.")
  );
}

/** Shared client giữ error.code của backend trong errorCode, không phải code MSG cũ. */
export function myAccountErrorMessage(error: unknown): string {
  if (isMyAccountAccessError(error)) return formatAppMessage("MSG-14");
  if (isApiError(error)) {
    switch (error.errorCode) {
      case "MSG-82":
        return formatAppMessage("MSG-82");
      case "MSG-08":
        return formatAppMessage("MSG-08");
      case "MSG-14":
        return formatAppMessage("MSG-14");
      case "MSG-04":
        return formatAppMessage("MSG-04");
    }
    if (error.status === 400) return formatAppMessage("MSG-08");
    if (error.status >= 500) return formatAppMessage("MSG-04");
  }
  return messageFromError(error);
}
