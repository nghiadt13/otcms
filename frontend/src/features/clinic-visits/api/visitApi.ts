import { apiRequest } from "@/lib/api/client";
import {
  DEFAULT_PAGE_SIZE,
  type PageQuery,
  type PageResponse,
} from "@/lib/api/pagination";
import type { VisitStatus } from "@/lib/domain/clinicVisit";
import type {
  VisitListItem,
  VisitListQuery,
  VisitProgress,
  VisitSummary,
} from "../types/visitProgress";

// Danh sách lượt khám (UC34) đọc từ GET /api/v1/me/visits: lượt đang mở và lượt check-in trong 7 ngày gần
// nhất của hồ sơ bản thân và người thân đã xác nhận (BR-19). BE trả cả danh sách, không phân trang, nên tab
// "Đang diễn ra", lọc theo người khám và chia trang được làm ở đây. Tiến độ một lượt là API thật.

const ACTIVE_STATUSES: readonly VisitStatus[] = [
  "WAITING_FOR_EXAMINATION",
  "IN_EXAMINATION",
  "AWAITING_CONCLUSION",
];

function summaryOf(item: VisitListItem): VisitSummary {
  return {
    id: item.visitId,
    visitDate: item.visitDate,
    status: item.status,
    patient: {
      id: item.patientProfileId,
      fullName: item.patientName,
      relationship: item.relationship,
    },
  };
}

/** Một trang của danh sách đã đủ dữ liệu; trang vượt quá số trang thì rỗng. */
function pageOf<T>(
  all: readonly T[],
  page: number,
  size: number,
): PageResponse<T> {
  return {
    items: all.slice(page * size, page * size + size),
    page,
    size,
    totalItems: all.length,
    totalPages: Math.ceil(all.length / size),
  };
}

/**
 * GET /api/v1/me/visits, rồi lọc theo `when` và `patientId` và chia trang. Giữ thứ tự BE (mới trước).
 * Lỗi của BE đi nguyên qua ApiError.
 */
export async function listVisits(
  query: VisitListQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<VisitSummary>> {
  const items = await apiRequest<VisitListItem[]>("/api/v1/me/visits", {
    signal,
  });
  const visible = items
    .map(summaryOf)
    .filter(
      (visit) =>
        query.when !== "active" || ACTIVE_STATUSES.includes(visit.status),
    )
    .filter(
      (visit) => !query.patientId || visit.patient.id === query.patientId,
    );
  return pageOf(visible, query.page ?? 0, query.size ?? DEFAULT_PAGE_SIZE);
}

/**
 * GET /api/v1/visits/{id}/progress (BE, UC34): số người phía trước, số thứ tự, có đang được gọi hay
 * không, và các chỉ định của lượt. Không trả kết quả khám (BR-35). BE trả 404 MSG-41 khi lượt không có
 * và 403 MSG-40 khi tài khoản không được xem lượt này; lỗi đi nguyên qua ApiError.
 */
export function getVisitProgress(
  visitId: string,
  signal?: AbortSignal,
): Promise<VisitProgress> {
  return apiRequest<VisitProgress>(
    `/api/v1/visits/${encodeURIComponent(visitId)}/progress`,
    { signal },
  );
}
