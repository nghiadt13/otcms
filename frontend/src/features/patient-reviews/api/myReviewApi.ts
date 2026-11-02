import type { ApiFieldError } from "@/lib/api/apiError";
import { clone, mockDelay, mockFail } from "@/lib/api/mockServer";
import type { ItemsResponse } from "@/lib/api/pagination";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import {
  validateReviewRating,
  validateReviewText,
} from "../schema/myReviewFieldRules";
import type {
  MyReview,
  ReviewPatient,
  ReviewableVisit,
  SubmitReviewRequest,
} from "../types/myReview";
import {
  myReviewStore,
  type MockReviewRecord,
  type MockReviewVisit,
} from "./myReviewMockData";

// LỚP API GIẢ cho đánh giá của bệnh nhân (UC59). Chữ ký khớp REST:
//   GET  /api/v1/me/reviews/eligible-visits → { items: ReviewableVisit[] }
//   GET  /api/v1/me/reviews                 → { items: MyReview[] }
//   POST /api/v1/reviews  (body SubmitReviewRequest) → 201 MyReview (PENDING)
// Khi nối BE: giữ tên và chữ ký hàm, thay thân bằng apiRequest. BE lọc theo phiên đăng nhập,
// FE không gửi id tài khoản. Lỗi là ProblemDetail + code MSG (xem api-notes/P08.md).

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;

function findVisit(visitId: string): MockReviewVisit | undefined {
  return myReviewStore.visits.find((visit) => visit.visitId === visitId);
}

/** Hồ sơ đã gắn với tài khoản có mã này; đã bỏ gắn thì lượt không còn thấy được (BR-19, BR-68). */
function linkedProfileOf(patientCode: string): LinkedProfile | undefined {
  return getLinkedProfileRecords().find(
    (profile) => profile.patientCode === patientCode,
  );
}

function patientOf(profile: LinkedProfile): ReviewPatient {
  return { fullName: profile.fullName, relationship: profile.relationship };
}

function toReviewable(
  visit: MockReviewVisit,
  profile: LinkedProfile,
): ReviewableVisit {
  return {
    visitId: visit.visitId,
    visitDate: visit.visitDate,
    serviceName: visit.serviceName,
    patient: patientOf(profile),
  };
}

function toMyReview(
  record: MockReviewRecord,
  visit: MockReviewVisit,
  profile: LinkedProfile,
): MyReview {
  return {
    id: record.id,
    visitId: record.visitId,
    visitDate: visit.visitDate,
    rating: record.rating,
    text: record.text,
    status: record.status,
    clinicReply: record.clinicReply,
    createdAt: record.createdAt,
    patient: patientOf(profile),
  };
}

/** ISO giờ Việt Nam (+07:00, không có giờ mùa hè). */
function vietnamIsoOf(now: Date): string {
  const shifted = new Date(now.getTime() + 7 * 60 * 60 * 1000);
  return `${shifted.toISOString().slice(0, 19)}+07:00`;
}

/** Lỗi trường của một yêu cầu gửi: điểm và nội dung đều kiểm như BE (MSG-57). */
function fieldErrorsOf(request: SubmitReviewRequest): ApiFieldError[] {
  const errors: ApiFieldError[] = [];
  const ratingCode = validateReviewRating(request.rating);
  if (ratingCode) errors.push({ field: "rating", code: ratingCode });
  const textCode = validateReviewText(request.text);
  if (textCode) errors.push({ field: "text", code: textCode });
  return errors;
}

/** GET /api/v1/me/reviews/eligible-visits: lượt COMPLETED của hồ sơ đã gắn, chưa có đánh giá. */
export async function listEligibleVisits(
  signal?: AbortSignal,
): Promise<ItemsResponse<ReviewableVisit>> {
  await mockDelay(LATENCY_MS, signal);
  if (myReviewStore.failList) mockFail(500, "MSG-04");
  const reviewed = new Set(
    myReviewStore.reviews.map((record) => record.visitId),
  );
  const items: ReviewableVisit[] = [];
  for (const visit of myReviewStore.visits) {
    if (!visit.completed || reviewed.has(visit.visitId)) continue;
    const profile = linkedProfileOf(visit.patientCode);
    if (profile) items.push(toReviewable(visit, profile));
  }
  items.sort(
    (a, b) =>
      b.visitDate.localeCompare(a.visitDate) ||
      a.visitId.localeCompare(b.visitId),
  );
  return clone({ items });
}

/** GET /api/v1/me/reviews: đánh giá đã gửi của hồ sơ đã gắn, mới nhất trước. */
export async function listMyReviews(
  signal?: AbortSignal,
): Promise<ItemsResponse<MyReview>> {
  await mockDelay(LATENCY_MS, signal);
  if (myReviewStore.failList) mockFail(500, "MSG-04");
  const items: MyReview[] = [];
  for (const record of myReviewStore.reviews) {
    const visit = findVisit(record.visitId);
    const profile = visit ? linkedProfileOf(visit.patientCode) : undefined;
    if (visit && profile) items.push(toMyReview(record, visit, profile));
  }
  items.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return clone({ items });
}

/**
 * POST /api/v1/reviews: gửi đánh giá, trạng thái PENDING chờ duyệt (BR-55).
 * 400 MSG-57 (kèm fieldErrors) khi điểm/nội dung sai; 403 MSG-56 khi lượt không thuộc tài khoản
 * hoặc không tồn tại; 409 MSG-56 khi lượt chưa hoàn tất hoặc đã đánh giá.
 */
export async function submitReview(
  request: SubmitReviewRequest,
  signal?: AbortSignal,
): Promise<MyReview> {
  await mockDelay(LATENCY_MS, signal);
  const fieldErrors = fieldErrorsOf(request);
  if (fieldErrors.length > 0) mockFail(400, "MSG-57", undefined, fieldErrors);

  const visit = findVisit(request.visitId);
  const profile = visit ? linkedProfileOf(visit.patientCode) : undefined;
  // Không lộ lượt của người khác: thiếu hoặc không thuộc tài khoản đều trả 403.
  if (!visit || !profile) mockFail(403, "MSG-56");
  if (!visit.completed) mockFail(409, "MSG-56");
  if (myReviewStore.reviews.some((record) => record.visitId === visit.visitId))
    mockFail(409, "MSG-56");

  const record: MockReviewRecord = {
    id: `rv-mock-${myReviewStore.reviews.length + 1}`,
    visitId: visit.visitId,
    // Đã kiểm là số nguyên 1–5 ở trên.
    rating: request.rating as MockReviewRecord["rating"],
    text: request.text.trim(),
    status: "PENDING",
    clinicReply: null,
    createdAt: vietnamIsoOf(new Date()),
  };
  myReviewStore.reviews.unshift(record);
  return clone(toMyReview(record, visit, profile));
}
