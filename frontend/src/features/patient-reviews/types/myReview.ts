import type { CareRelationship } from "@/lib/domain/careRelationship";

// Đánh giá của bệnh nhân (UC59): GET /api/v1/me/reviews, GET /api/v1/me/reviews/eligible-visits,
// POST /api/v1/reviews. Khác PublicReview (công khai) và ReviewDetail (kiểm duyệt admin).
// `patient` có ở cả hai kiểu để luôn ghi "của ai" (tên + quan hệ) như quy tắc khu bệnh nhân.

export type MyReviewStatus = "PENDING" | "PUBLISHED" | "HIDDEN";

/** Người khám của lượt: tên và quan hệ với tài khoản đang đăng nhập. */
export interface ReviewPatient {
  fullName: string;
  relationship: CareRelationship;
}

/** Lượt khám đã hoàn tất của hồ sơ đã gắn mà tài khoản chưa đánh giá (BR-54). */
export interface ReviewableVisit {
  visitId: string;
  /** YYYY-MM-DD */
  visitDate: string;
  serviceName: string;
  patient: ReviewPatient;
}

export interface MyReview {
  id: string;
  visitId: string;
  /** YYYY-MM-DD */
  visitDate: string;
  rating: 1 | 2 | 3 | 4 | 5;
  text: string;
  status: MyReviewStatus;
  /** Phản hồi của phòng khám; null khi chưa có. */
  clinicReply: string | null;
  /** ISO có offset +07:00: lúc gửi đánh giá. */
  createdAt: string;
  patient: ReviewPatient;
}

export interface SubmitReviewRequest {
  visitId: string;
  rating: number;
  text: string;
}
