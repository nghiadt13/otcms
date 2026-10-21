import type { PageResponse } from "@/lib/api/pagination";

// Kiểu dữ liệu màn kiểm duyệt đánh giá (admin). Khớp JSON của /api/v1/admin/reviews.
// Khác `PatientReview` (public, chỉ đánh giá đã công khai).

export type ReviewStatus = "PENDING" | "PUBLISHED" | "HIDDEN";
export type ReviewAction = "approve" | "hide" | "unhide" | "reply" | "editReply";
export type ReviewRating = 1 | 2 | 3 | 4 | 5;

export interface ReviewVisit {
  visitDate: string; // "2026-10-18"
  doctorName: string;
  serviceName: string;
}

/** Một dòng bảng (list). */
export interface ReviewListItem {
  id: string;
  submittedAt: string; // "2026-10-19T14:32:00+07:00"
  patientName: string;
  rating: ReviewRating;
  content: string; // "" = không có nội dung chữ
  status: ReviewStatus;
  replied: boolean;
  visit: ReviewVisit;
  allowedActions: ReviewAction[];
}

export interface ReviewReply {
  text: string;
  repliedAt: string;
}

/** GET theo id; cũng là response của approve/hide/unhide/reply. */
export interface ReviewDetail extends ReviewListItem {
  reply: ReviewReply | null;
}

export interface ReviewCounts {
  pending: number;
  published: number;
  hidden: number;
  all: number;
}

export type ReviewListPage = PageResponse<ReviewListItem, { counts: ReviewCounts }>;

export interface ReviewReplyRequest {
  text: string;
}

/** State bộ lọc. Chuỗi rỗng = không lọc; "ALL" chỉ tồn tại ở UI và map về rỗng. */
export interface ReviewFilters {
  status: ReviewStatus | "";
  rating: "" | "1" | "2" | "3" | "4" | "5";
  replied: "" | "true" | "false";
  from: string; // "YYYY-MM-DD"
  to: string;
}
