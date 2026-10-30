import type { VisitStatus, VisitType } from "@/lib/domain/clinicVisit";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import type {
  VisitOrderProgress,
  VisitQueuePosition,
} from "../types/visitProgress";

// Dữ liệu giả của lượt khám (UC34). Mã lượt và mã chỉ định khớp với mock thanh toán
// (features/billing: vis-an-today, vis-lan-await, svc-*) và lịch hẹn apt-2001 → vis-an-today.
// Hai lượt đang khám giữ ngày cố định (23/10, 22/10) như mock thanh toán; các lượt khác tính
// theo hôm nay để luôn nằm trong 30 ngày của tab "Gần đây".

export interface VisitSeed {
  id: string;
  /** `LinkedProfile.patientCode` của hồ sơ sở hữu lượt. */
  patientCode: string;
  visitDate: string;
  status: VisitStatus;
  type: VisitType;
  /** Hàng đợi tiến dần theo số lần đọc tiến độ (mô phỏng): về 0 thì đang gọi. */
  advancing: boolean;
  returnDeadline: string | null;
  queues: VisitQueuePosition[];
  orders: VisitOrderProgress[];
}

const AN = "BN-000123";
const LAN = "BN-000456";
const HUY = "BN-000789";

export function visitSeeds(now: Date): VisitSeed[] {
  const today = todayIsoDate(now);
  return [
    {
      id: "vis-an-today",
      patientCode: AN,
      visitDate: "2026-10-23",
      status: "WAITING_FOR_EXAMINATION",
      type: "FIRST_VISIT",
      advancing: true,
      returnDeadline: null,
      queues: [
        {
          roomName: "Phòng khám 2",
          queueType: "EXAMINATION",
          queueNumber: 12,
          peopleAhead: 3,
          status: "WAITING",
        },
      ],
      orders: [
        {
          id: "svc-pending",
          serviceName: "MRI cột sống",
          kind: "IMAGING",
          roomName: null,
          status: "PENDING_PAYMENT",
          cancellationReason: null,
        },
        {
          id: "svc-cbc",
          serviceName: "Công thức máu",
          kind: "LABORATORY",
          roomName: null,
          status: "PENDING_PAYMENT",
          cancellationReason: null,
        },
        {
          id: "svc-xray-knee",
          serviceName: "X-quang khớp gối",
          kind: "IMAGING",
          roomName: "Phòng X-quang 1",
          status: "PAID",
          cancellationReason: null,
        },
        {
          id: "svc-us-shoulder",
          serviceName: "Siêu âm khớp vai",
          kind: "IMAGING",
          roomName: null,
          status: "CANCELLED",
          cancellationReason: "Bác sĩ thay bằng MRI",
        },
      ],
    },
    {
      id: "vis-lan-await",
      patientCode: LAN,
      visitDate: "2026-10-22",
      status: "AWAITING_CONCLUSION",
      type: "FOLLOW_UP",
      advancing: false,
      returnDeadline: addDays(today, 5),
      queues: [
        {
          roomName: "Phòng X-quang 1",
          queueType: "SERVICE",
          queueNumber: 5,
          peopleAhead: 1,
          status: "WAITING",
        },
        {
          roomName: "Phòng xét nghiệm",
          queueType: "SERVICE",
          queueNumber: 8,
          peopleAhead: 0,
          status: "BEING_SERVED",
        },
      ],
      orders: [
        {
          id: "svc-lan-xray",
          serviceName: "X-quang khớp gối",
          kind: "IMAGING",
          roomName: "Phòng X-quang 1",
          status: "PAID",
          cancellationReason: null,
        },
        {
          id: "svc-lan-cbc",
          serviceName: "Công thức máu",
          kind: "LABORATORY",
          roomName: "Phòng xét nghiệm",
          status: "PAID",
          cancellationReason: null,
        },
        {
          id: "svc-endoscopy",
          serviceName: "Nội soi dạ dày",
          kind: "IMAGING",
          roomName: null,
          status: "PENDING_PAYMENT",
          cancellationReason: null,
        },
      ],
    },
    {
      id: "vis-an-done",
      patientCode: AN,
      visitDate: addDays(today, -14),
      status: "COMPLETED",
      type: "FIRST_VISIT",
      advancing: false,
      returnDeadline: null,
      queues: [],
      orders: [
        {
          id: "svc-done-xray",
          serviceName: "X-quang cột sống",
          kind: "IMAGING",
          roomName: "Phòng X-quang 1",
          status: "RESULTS_AVAILABLE",
          cancellationReason: null,
        },
        {
          id: "svc-done-cbc",
          serviceName: "Công thức máu",
          kind: "LABORATORY",
          roomName: "Phòng xét nghiệm",
          status: "RESULTS_AVAILABLE",
          cancellationReason: null,
        },
      ],
    },
    {
      id: "vis-lan-incomplete",
      patientCode: LAN,
      visitDate: addDays(today, -16),
      status: "INCOMPLETE",
      type: "FOLLOW_UP",
      advancing: false,
      returnDeadline: null,
      queues: [],
      orders: [
        {
          id: "svc-inc-us",
          serviceName: "Siêu âm khớp vai",
          kind: "IMAGING",
          roomName: "Phòng siêu âm",
          status: "RESULTS_AVAILABLE",
          cancellationReason: null,
        },
      ],
    },
    {
      id: "vis-huy-left",
      patientCode: HUY,
      visitDate: today,
      status: "CANCELLED_LEFT",
      type: "FIRST_VISIT",
      advancing: false,
      returnDeadline: null,
      queues: [],
      orders: [],
    },
  ];
}
