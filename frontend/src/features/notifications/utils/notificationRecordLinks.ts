import { ADMIN_ROUTES } from "@/lib/routes/adminRoutes";
import type {
  NotificationRecordKind,
  NotificationRelatedRecord,
} from "../types/notification";

/** Nhãn loại bản ghi liên quan, hiện trong chi tiết thông báo. */
export const RECORD_LABELS: Record<NotificationRecordKind, string> = {
  APPOINTMENT: "Lịch hẹn",
  VISIT: "Lượt khám",
  VISIT_RESULT: "Kết quả khám",
  PAYMENT: "Thanh toán",
};

/** Bản ghi liên quan → đường dẫn màn; null nghĩa là không mở được (ẩn nút Mở). */
export type NotificationRecordHrefResolver = (
  record: NotificationRelatedRecord,
) => string | null;

/** Mặc định cho khu admin (giữ hành vi cũ): chỉ lịch hẹn có màn đích. */
export const adminRecordHref: NotificationRecordHrefResolver = (record) =>
  record.kind === "APPOINTMENT" ? ADMIN_ROUTES.appointments : null;
