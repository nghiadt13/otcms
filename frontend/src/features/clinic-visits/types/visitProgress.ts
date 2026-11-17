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
  /** Số người đứng trước trong hàng. */
  peopleAhead: number | null;
  /** Bệnh nhân đang được gọi vào phòng. */
  beingCalled: boolean;
  serviceOrders: VisitProgressOrder[];
}

export interface VisitListQuery {
  when: VisitWhen;
  /** uuid hồ sơ đã gắn. Vắng = mọi hồ sơ của tài khoản. */
  patientId?: string;
}
