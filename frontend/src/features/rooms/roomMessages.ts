import { toast } from "sonner";
import type { ApiError } from "@/lib/api/apiError";
import { notifyError } from "@/lib/messages/notify";

// Câu thông báo riêng của màn Phòng theo đúng nguyên văn spec 06 (MSG-61/63/64).
// Dùng cục bộ vì bảng chung `APP_MESSAGES` đang ghi lệch nghĩa các mã này.

export const ROOM_SAVED_TEXT = "Đã lưu thông tin phòng.";

export const roomStatusUpdatedText = (statusLabel: string) =>
  `Đã cập nhật trạng thái phòng thành ${statusLabel}.`;

export const ROOM_IN_USE_TEXT =
  "Phòng đang được sử dụng hoặc đã được phân công. Vui lòng kiểm tra trước khi đổi trạng thái.";

/** Toast lỗi cho thao tác ghi; 409 MSG-64 dùng câu của màn này. */
export function notifyRoomError(error: unknown) {
  const apiError = error as Partial<ApiError> | null;
  if (apiError?.status === 409 && apiError.code === "MSG-64") {
    toast.error(ROOM_IN_USE_TEXT);
    return;
  }
  notifyError(error);
}
