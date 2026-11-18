import { toast } from "sonner";
import type { ApiError } from "@/lib/api/apiError";
import { notifyError } from "@/lib/messages/notify";
import { APP_MESSAGES, formatAppMessage } from "@/lib/messages/appMessages";

export const ROOM_SAVED_TEXT = APP_MESSAGES["MSG-61"];

export const roomStatusUpdatedText = (statusLabel: string) =>
  formatAppMessage("MSG-63", { status: statusLabel });

export const ROOM_IN_USE_TEXT = APP_MESSAGES["MSG-64"];

/** Toast lỗi cho thao tác ghi; 409 MSG-64 dùng câu của màn này. */
export function notifyRoomError(error: unknown) {
  const apiError = error as Partial<ApiError> | null;
  if (apiError?.status === 409 && apiError.code === "MSG-64") {
    toast.error(ROOM_IN_USE_TEXT);
    return;
  }
  notifyError(error);
}
