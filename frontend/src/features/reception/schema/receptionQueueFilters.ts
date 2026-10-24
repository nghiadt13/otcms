import type { ReceptionQueueRow, ReceptionQueueStatus } from "../types/receptionQueue";
import { foldSearchText } from "./appointmentRowFilter";

// Bộ lọc của màn Hàng đợi tiếp nhận. Phòng và trạng thái gửi lên BE (query). Tên hoặc số thứ tự lọc
// tại FE, không đưa lên URL (tên bệnh nhân không được nằm trên đường dẫn).

export type StatusFilterValue = "WAITING_ABSENT" | "WAITING" | "ABSENT" | "ALL";

export const STATUS_FILTER_LABELS: Record<StatusFilterValue, string> = {
  WAITING_ABSENT: "Đang chờ + Vắng mặt",
  WAITING: "Đang chờ",
  ABSENT: "Vắng mặt",
  ALL: "Tất cả",
};

/** Trạng thái gửi lên BE cho từng lựa chọn. "Tất cả" thêm Đang phục vụ. */
export const STATUS_FILTER_STATUSES: Record<StatusFilterValue, ReceptionQueueStatus[]> = {
  WAITING_ABSENT: ["WAITING", "ABSENT"],
  WAITING: ["WAITING"],
  ABSENT: ["ABSENT"],
  ALL: ["WAITING", "BEING_SERVED", "ABSENT"],
};

export interface ReceptionQueueFilterState {
  /** Chuỗi rỗng = tất cả phòng. */
  roomId: string;
  status: StatusFilterValue;
  /** Ô lọc tên hoặc số thứ tự (chỉ FE). */
  search: string;
}

export const INITIAL_QUEUE_FILTER: ReceptionQueueFilterState = {
  roomId: "",
  status: "WAITING_ABSENT",
  search: "",
};

/** Khớp tên (không phân biệt dấu và hoa thường) hoặc số thứ tự (cột Số) đúng bằng ô nhập. Ô trống khớp mọi dòng. */
export function matchesReceptionQueueSearch(
  row: Pick<ReceptionQueueRow, "patientName" | "queueNumber">,
  search: string,
): boolean {
  const text = search.trim();
  if (!text) return true;
  return (
    foldSearchText(row.patientName).includes(foldSearchText(text)) ||
    String(row.queueNumber) === text
  );
}
