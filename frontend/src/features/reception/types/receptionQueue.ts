import type {
  QueueEntryStatus,
  QueueType,
  VisitStatus,
  VisitType,
} from "@/lib/domain/clinicVisit";

// Hợp đồng dữ liệu của màn Hàng đợi tiếp nhận (UC30, chỉ xem). Tên trường theo JSON của BE
// (camelCase), enum giữ nguyên UPPER_SNAKE. Chi tiết endpoint: api-notes/S05.md.

/** Trạng thái hàng đợi màn này hiển thị. Xong và Đã đóng không hiện. */
export type ReceptionQueueStatus = Extract<
  QueueEntryStatus,
  "WAITING" | "BEING_SERVED" | "ABSENT"
>;

/** Hàng đợi khám và đọc kết quả. Hàng đợi dịch vụ do kỹ thuật viên quản (UC46), không hiện ở đây. */
export type ReceptionQueueType = Extract<QueueType, "EXAMINATION" | "RESULT_REVIEW">;

export interface ReceptionQueueRow {
  queueEntryId: string;
  queueNumber: number;
  /** Vị trí trong hàng của phòng khi Đang chờ (BE sắp sẵn); null khi khác. */
  position: number | null;
  patientName: string;
  patientCode: string;
  roomId: string;
  roomName: string;
  queueType: ReceptionQueueType;
  /** null = vãng lai hoặc quay lại trong 7 ngày. */
  appointmentStartAt: string | null;
  priorityLabel: string | null;
  status: ReceptionQueueStatus;
  joinedAt: string;
}

export interface ReceptionRoomOption {
  id: string;
  name: string;
}

export interface ReceptionQueueResponse {
  items: ReceptionQueueRow[];
  /**
   * Đếm theo cả ngày, không phụ thuộc bộ lọc. `total` = bệnh nhân đã tiếp nhận hôm nay vào hàng
   * khám hoặc đọc kết quả, kể cả đã xong (không hiện trong danh sách mặc định).
   */
  counts: { total: number; waiting: number; absent: number };
  rooms: ReceptionRoomOption[];
  generatedAt: string;
}

/** Query của GET queue. Không truyền `status` thì BE dùng Đang chờ + Vắng mặt. */
export interface ReceptionQueueQuery {
  roomId?: string;
  status?: ReceptionQueueStatus[];
}

/** Tóm tắt lượt khám khi chọn một dòng: chỉ dữ liệu hành chính và hàng đợi (không tiền sử, dị ứng). */
export interface ReceptionVisitSummary {
  queueEntryId: string;
  queueNumber: number;
  roomName: string;
  patient: { fullName: string; patientCode: string; sex: "MALE" | "FEMALE"; age: number };
  checkedInAt: string;
  appointmentStartAt: string | null;
  visitType: VisitType;
  responsibleDoctorName: string;
  priorityLabel: string | null;
  queueType: ReceptionQueueType;
  queueStatus: ReceptionQueueStatus;
  /** Số người đứng trước trong hàng của phòng khi Đang chờ; null khi khác. */
  peopleAhead: number | null;
  visitStatus: VisitStatus;
}
