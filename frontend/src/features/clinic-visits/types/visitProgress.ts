import type { CareRelationship } from "@/lib/domain/careRelationship";
import type {
  QueueEntryStatus,
  QueueType,
  ServiceOrderStatus,
  VisitStatus,
  VisitType,
} from "@/lib/domain/clinicVisit";

// Hợp đồng dữ liệu tiến độ lượt khám (UC34). Tên trường khớp REST: camelCase, enum UPPER_SNAKE.
// Không có kết quả khám ở đây (BR-35): kết quả đi qua khu Kết quả khám.

/** `active`: Chờ khám, Đang khám, Chờ kết luận. `recent`: mọi trạng thái trong 30 ngày gần nhất. */
export type VisitWhen = "active" | "recent";

/** Việc BE cho phép trên lượt khám. FE chỉ vẽ nút có trong `allowedActions`. */
export type VisitAction = "payOrders" | "viewResults" | "review";

export type ServiceKind = "IMAGING" | "LABORATORY";

export interface VisitPatient {
  id: string;
  fullName: string;
  relationship: CareRelationship;
}

export interface VisitSummary {
  id: string;
  /** YYYY-MM-DD */
  visitDate: string;
  status: VisitStatus;
  type: VisitType;
  patient: VisitPatient;
}

/** Vị trí trong một hàng đợi phòng mà bệnh nhân đang chờ (khám, dịch vụ hoặc đọc kết quả). */
export interface VisitQueuePosition {
  roomName: string;
  queueType: QueueType;
  queueNumber: number;
  /** Số người đứng trước trong cùng hàng. */
  peopleAhead: number;
  status: QueueEntryStatus;
}

export interface VisitOrderProgress {
  id: string;
  serviceName: string;
  kind: ServiceKind;
  /** Phòng thực hiện; `null` khi chưa gắn phòng. */
  roomName: string | null;
  status: ServiceOrderStatus;
  /** Chỉ có khi `status` là `CANCELLED`. */
  cancellationReason: string | null;
}

export interface VisitProgress extends VisitSummary {
  /** ISO +07:00: thời điểm BE tính tiến độ. */
  updatedAt: string;
  /** YYYY-MM-DD, chỉ có khi lượt Chờ kết luận. BE tính; FE không tự cộng ngày. */
  returnDeadline: string | null;
  queues: VisitQueuePosition[];
  orders: VisitOrderProgress[];
  allowedActions: VisitAction[];
}

export interface VisitListQuery {
  when: VisitWhen;
  /** uuid hồ sơ đã gắn. Vắng = mọi hồ sơ của tài khoản. */
  patientId?: string;
}
