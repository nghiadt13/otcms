import { apiRequest } from "@/lib/api/client";
import { clone, mockDelay, mockFail } from "@/lib/api/mockServer";
import type {
  ReceptionQueueQuery,
  ReceptionQueueResponse,
  ReceptionQueueStatus,
  ReceptionVisitSummary,
} from "../types/receptionQueue";
import {
  getReceptionQueueMock,
  getReceptionVisitSummaryMock,
} from "./receptionQueueMockQueries";
import { takeForcedReceptionQueueFailure } from "./receptionQueueMockControls";

// Lớp API giả cho màn Hàng đợi tiếp nhận (UC30, chỉ xem). Chữ ký khớp REST của BE; khi nối BE đặt
// USE_MOCK = false (nhánh apiRequest đã viết sẵn). Đầu vào được kiểm ở ranh giới trước khi gọi.

const USE_MOCK = true;

/** Trạng thái mặc định khi không truyền `status`: Đang chờ và Vắng mặt (giống BE). */
export const RECEPTION_QUEUE_DEFAULT_STATUSES: readonly ReceptionQueueStatus[] = ["WAITING", "ABSENT"];

const ALLOWED_STATUSES: readonly ReceptionQueueStatus[] = ["WAITING", "BEING_SERVED", "ABSENT"];

// Trong test không chờ bằng timer (độ trễ giả sẽ lệch với đồng hồ giả); ngoài test giữ 300 ms.
const latency = (signal?: AbortSignal) =>
  process.env.NODE_ENV === "test" ? Promise.resolve() : mockDelay(300, signal);

/**
 * GET /api/v1/reception/queue?roomId=&status=… (status lặp). FE luôn gửi `status` tường minh.
 * Sai trạng thái, mã phòng rỗng hoặc phòng lạ → 400 MSG-39. Không liên quan quyền ở đây.
 */
export async function getReceptionQueue(
  query: ReceptionQueueQuery = {},
  signal?: AbortSignal,
): Promise<ReceptionQueueResponse> {
  const statuses = query.status ?? RECEPTION_QUEUE_DEFAULT_STATUSES;
  if (statuses.length === 0 || !statuses.every((status) => ALLOWED_STATUSES.includes(status))) {
    mockFail(400, "MSG-39");
  }
  if (query.roomId !== undefined && query.roomId.trim() === "") mockFail(400, "MSG-39");
  if (!USE_MOCK) {
    const params = new URLSearchParams();
    if (query.roomId) params.set("roomId", query.roomId);
    for (const status of statuses) params.append("status", status);
    return apiRequest<ReceptionQueueResponse>(
      `/api/v1/reception/queue?${params.toString()}` as const,
      { signal },
    );
  }
  await latency(signal);
  takeForcedReceptionQueueFailure("queue");
  return clone(getReceptionQueueMock({ roomId: query.roomId, statuses }));
}

/** GET /api/v1/reception/queue/{queueEntryId}: 404/410 MSG-38 khi bản ghi đã rời hàng. */
export async function getReceptionVisitSummary(
  queueEntryId: string,
  signal?: AbortSignal,
): Promise<ReceptionVisitSummary> {
  if (queueEntryId.trim() === "") mockFail(400, "MSG-39");
  if (!USE_MOCK) {
    return apiRequest<ReceptionVisitSummary>(
      `/api/v1/reception/queue/${encodeURIComponent(queueEntryId)}` as const,
      { signal },
    );
  }
  await latency(signal);
  takeForcedReceptionQueueFailure("summary");
  return clone(getReceptionVisitSummaryMock(queueEntryId));
}
