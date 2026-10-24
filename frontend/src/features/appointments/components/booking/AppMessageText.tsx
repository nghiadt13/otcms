import type { MsgCode } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import type { BookingVariant } from "../../types/booking";
import { bookingSize } from "./bookingSizes";

// Câu thông báo trong khối (không phải toast) lấy từ bảng MSG dùng chung, để cùng chữ với toast.

const TONE_CLASS = {
  warning: "text-warning",
  muted: "text-muted-foreground",
} as const;

export function AppMessageText({
  code,
  variant,
  tone,
}: {
  code: MsgCode;
  variant: BookingVariant;
  tone: keyof typeof TONE_CLASS;
}) {
  return (
    <p role="status" className={`${bookingSize(variant).body} ${TONE_CLASS[tone]}`}>
      {formatAppMessage(code)}
    </p>
  );
}
