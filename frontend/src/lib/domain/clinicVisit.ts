import type { StatusTone } from "@/components/admin/StatusBadge";

// Enum, nhãn và tone (cho StatusBadge) dùng chung giữa lịch hẹn, lễ tân, lượt khám,
// thanh toán và admin, để các khu hiển thị cùng một chữ cho cùng một trạng thái.
// Mã giữ nguyên theo hợp đồng API (UPPER_SNAKE). Chỉ thêm giá trị, không đổi tên giá
// trị đã có: plan lễ tân cũng dùng file này.

export type AppointmentStatus =
  "BOOKED" | "CHECKED_IN" | "CANCELLED" | "NO_SHOW";
export type VisitStatus =
  | "WAITING_FOR_EXAMINATION"
  | "IN_EXAMINATION"
  | "AWAITING_CONCLUSION"
  | "COMPLETED"
  | "INCOMPLETE"
  | "CANCELLED_LEFT";
export type VisitType = "FIRST_VISIT" | "FOLLOW_UP";
export type QueueType = "EXAMINATION" | "SERVICE" | "RESULT_REVIEW";
export type QueueEntryStatus =
  "WAITING" | "BEING_SERVED" | "DONE" | "ABSENT" | "CLOSED";
export type ServiceOrderStatus =
  "PENDING_PAYMENT" | "PAID" | "RESULTS_AVAILABLE" | "CANCELLED";
export type ExamFeeStatus = "PAID" | "PENDING_PAYMENT" | "NOT_REQUIRED";
/** R3 ERD: Cash, VNPay. Thu bằng QR tại quầy cũng là VNPAY. */
export type PaymentMethod = "CASH" | "VNPAY";
export type PaymentStatus = "SUCCESS" | "PENDING" | "FAILED";

export const APPOINTMENT_STATUS_LABELS: Record<AppointmentStatus, string> = {
  BOOKED: "Đã đặt",
  CHECKED_IN: "Đã tiếp nhận",
  CANCELLED: "Đã hủy",
  NO_SHOW: "Không đến",
};
export const APPOINTMENT_STATUS_TONE: Record<AppointmentStatus, StatusTone> = {
  BOOKED: "info",
  CHECKED_IN: "success",
  CANCELLED: "neutral",
  NO_SHOW: "warning",
};

export const VISIT_STATUS_LABELS: Record<VisitStatus, string> = {
  WAITING_FOR_EXAMINATION: "Chờ khám",
  IN_EXAMINATION: "Đang khám",
  AWAITING_CONCLUSION: "Chờ kết luận",
  COMPLETED: "Đã hoàn tất",
  INCOMPLETE: "Chưa hoàn tất",
  CANCELLED_LEFT: "Đã rời đi",
};
export const VISIT_STATUS_TONE: Record<VisitStatus, StatusTone> = {
  WAITING_FOR_EXAMINATION: "info",
  IN_EXAMINATION: "success",
  AWAITING_CONCLUSION: "info",
  COMPLETED: "success",
  INCOMPLETE: "warning",
  CANCELLED_LEFT: "neutral",
};

/** Nhãn loại lượt khám. Không có tone: loại không phải trạng thái. */
export const VISIT_TYPE_LABELS: Record<VisitType, string> = {
  FIRST_VISIT: "Khám lần đầu",
  FOLLOW_UP: "Tái khám",
};

/** Nhãn loại hàng đợi. Không có tone: loại không phải trạng thái. */
export const QUEUE_TYPE_LABELS: Record<QueueType, string> = {
  EXAMINATION: "Khám bệnh",
  SERVICE: "Dịch vụ",
  RESULT_REVIEW: "Trả kết quả",
};

export const QUEUE_STATUS_LABELS: Record<QueueEntryStatus, string> = {
  WAITING: "Đang chờ",
  BEING_SERVED: "Đang phục vụ",
  DONE: "Xong",
  ABSENT: "Vắng mặt",
  CLOSED: "Đã đóng",
};
export const QUEUE_STATUS_TONE: Record<QueueEntryStatus, StatusTone> = {
  WAITING: "info",
  BEING_SERVED: "success",
  DONE: "neutral",
  ABSENT: "warning",
  CLOSED: "neutral",
};

export const SERVICE_ORDER_STATUS_LABELS: Record<ServiceOrderStatus, string> = {
  PENDING_PAYMENT: "Chờ thanh toán",
  PAID: "Đã thanh toán",
  RESULTS_AVAILABLE: "Đã có kết quả",
  CANCELLED: "Đã hủy",
};
export const SERVICE_ORDER_STATUS_TONE: Record<ServiceOrderStatus, StatusTone> =
  {
    PENDING_PAYMENT: "warning",
    PAID: "info",
    RESULTS_AVAILABLE: "success",
    CANCELLED: "neutral",
  };

export const EXAM_FEE_STATUS_LABELS: Record<ExamFeeStatus, string> = {
  PAID: "Đã thanh toán",
  PENDING_PAYMENT: "Chưa thanh toán",
  NOT_REQUIRED: "Không thu",
};
export const EXAM_FEE_STATUS_TONE: Record<ExamFeeStatus, StatusTone> = {
  PAID: "success",
  PENDING_PAYMENT: "warning",
  NOT_REQUIRED: "neutral",
};

export const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  CASH: "Tiền mặt",
  VNPAY: "VNPay",
};

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  SUCCESS: "Thành công",
  PENDING: "Đang chờ xác nhận",
  FAILED: "Thất bại",
};
export const PAYMENT_STATUS_TONE: Record<PaymentStatus, StatusTone> = {
  SUCCESS: "success",
  PENDING: "warning",
  FAILED: "danger",
};
