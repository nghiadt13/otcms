import type { MsgCode } from "@/lib/api/apiError";
import { mockFail } from "@/lib/api/mockServer";

// Công cụ CHỈ DÀNH CHO TEST của lớp API giả hàng đợi: buộc lần gọi kế tiếp lỗi (đúng mã BE:
// 403/404/410 → MSG-38, 5xx → MSG-04), hoặc làm hàng đợi trống (AF1). Không nằm trong kho giả để
// kho chỉ có đọc thêm.

export type ReceptionQueueCall = "queue" | "summary";
export type ForcedQueueStatus = 403 | 404 | 410 | 500;

const FORCED_CODE: Record<ForcedQueueStatus, MsgCode> = {
  403: "MSG-38",
  404: "MSG-38",
  410: "MSG-38",
  500: "MSG-04",
};

const forced: Record<ReceptionQueueCall, ForcedQueueStatus[]> = { queue: [], summary: [] };
let queueForcedEmpty = false;

/** Lần gọi `call` kế tiếp (lặp `times` lần) sẽ bị từ chối với `status`. */
export function forceNextReceptionQueueFailure(
  call: ReceptionQueueCall,
  status: ForcedQueueStatus,
  times = 1,
): void {
  for (let i = 0; i < times; i += 1) forced[call].push(status);
}

/** Dùng trong lớp giả: nếu có lỗi đã hẹn cho lần gọi này thì ném đúng hình ProblemDetail. */
export function takeForcedReceptionQueueFailure(call: ReceptionQueueCall): void {
  const status = forced[call].shift();
  if (status !== undefined) mockFail(status, FORCED_CODE[status]);
}

/** Làm hàng đợi trống (trạng thái AF1) cho đến khi reset. */
export function setReceptionQueueForcedEmpty(empty: boolean): void {
  queueForcedEmpty = empty;
}

export function isReceptionQueueForcedEmpty(): boolean {
  return queueForcedEmpty;
}

/** Xóa mọi cờ test; gọi trong beforeEach của test màn hàng đợi. */
export function resetReceptionQueueMockControls(): void {
  forced.queue.length = 0;
  forced.summary.length = 0;
  queueForcedEmpty = false;
}
