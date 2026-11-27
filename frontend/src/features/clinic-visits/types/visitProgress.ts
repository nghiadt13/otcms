import type { CareRelationship } from "@/lib/domain/careRelationship";
import type {
  ClinicalOrderPaymentStatus,
  ClinicalOrderStatus,
  VisitStatus,
} from "@/lib/domain/clinicVisit";

// Hợp đồng dữ liệu lượt khám khu bệnh nhân (UC34). Danh sách lượt (VisitListItem từ GET /api/v1/me/visits)
// và tiến độ (VisitProgress từ GET /api/v1/visits/{id}/progress) đều là dữ liệu BE. Không có kết quả khám
// ở đây (BR-35): kết quả đi qua khu Kết quả khám.

/** `active`: Chờ khám, Đang khám, Chờ kết luận. `recent`: mọi lượt trong danh sách (đang mở và 7 ngày gần nhất). */
export type VisitWhen = "active" | "recent";

export interface VisitPatient {
  id: string;
  fullName: string;
  /** null khi liên kết cũ chưa có quan hệ R3: chỉ hiện tên. */
  relationship: CareRelationship | null;
}

/** Một dòng của danh sách lượt (BE: MyVisitItemResponse). */
export interface VisitListItem {
  visitId: string;
  patientProfileId: string;
  patientName: string;
  relationship: CareRelationship | null;
  /** YYYY-MM-DD theo giờ phòng khám. */
  visitDate: string;
  /** Thời điểm check-in (ISO 8601). */
  checkedInAt: string;
  status: VisitStatus;
  /** Số thứ tự khi lượt đang trong hàng khám hoặc dịch vụ; null khi không ở hàng. */
  queueNumber: number | null;
  /** Tên phòng của hàng đang chờ; null khi không ở hàng. */
  roomName: string | null;
}

/** Vị trí trong một hàng phòng (BE: VisitProgressResponse.QueueInfo). */
export interface VisitQueueInfo {
  queueNumber: number | null;
  status: string;
  roomCode: string | null;
  peopleAhead: number;
  beingCalled: boolean;
}

/** Phiếu số đã chuẩn hoá để vẽ: dùng cho hàng khám, hàng của chỉ định và thẻ tóm tắt ở trang chủ. */
export interface QueueTicket {
  queueNumber: number;
  peopleAhead: number | null;
  beingCalled: boolean;
  room: string | null;
  stage: "EXAM" | "SERVICE" | null;
}

export interface VisitSummary {
  id: string;
  /** YYYY-MM-DD */
  visitDate: string;
  status: VisitStatus;
  patient: VisitPatient;
}

/** Chỉ định trong tiến độ (BE: VisitProgressResponse.ServiceOrder). */
export interface VisitProgressOrder {
  orderId: string;
  orderCode: string;
  serviceName: string;
  status: ClinicalOrderStatus;
  paymentStatus: ClinicalOrderPaymentStatus;
  roomId: string | null;
  /** Tên phòng (nhãn hiển thị) khi chỉ định đã có phòng; null khi chưa có. */
  roomName: string | null;
  /** Vị trí trong hàng của phòng thực hiện; null khi chỉ định chưa vào hàng. */
  queue: VisitQueueInfo | null;
}

/** Tiến độ một lượt khám (BE: VisitProgressResponse). Các trường vào hàng đợi là null khi chưa vào hàng. */
export interface VisitProgress {
  visitId: string;
  source: "SCHEDULED" | "WALK_IN";
  /** Trạng thái thô của lượt trong bảng visits; không phải trạng thái khám R3, FE không dùng để hiện chữ. */
  status: string;
  /** Bước chăm sóc đang làm, hoặc trạng thái lượt khi không có bước. */
  currentStage: string | null;
  arrivedAt: string | null;
  /** Vị trí của lượt vãng lai đang chờ trong hàng tiếp nhận. */
  queuePosition: number | null;
  /** Tên phòng của hàng đang chờ. */
  currentRoom: string | null;
  /** Số thứ tự trong hàng đang chờ. */
  queueNumber: number | null;
  /** Hàng đang chờ là hàng khám bệnh (EXAM) hay hàng làm chỉ định (SERVICE); null khi không ở hàng phòng. */
  queueStage: "EXAM" | "SERVICE" | null;
  /** Số người đứng trước trong hàng. */
  peopleAhead: number | null;
  /** Bệnh nhân đang được gọi vào phòng. */
  beingCalled: boolean;
  serviceOrders: VisitProgressOrder[];
  /** Hàng khám bệnh của lượt; null khi không ở hàng khám. Tách riêng khỏi hàng của từng chỉ định. */
  examQueue: VisitQueueInfo | null;
}

export interface VisitListQuery {
  when: VisitWhen;
  /** uuid hồ sơ đã gắn. Vắng = mọi hồ sơ của tài khoản. */
  patientId?: string;
}
