import { getReceptionVisitSummary } from "../api/receptionQueueApi";
import { useReceptionLoader } from "./useReceptionLoader";

/** Tóm tắt lượt khám của một dòng hàng đợi; đổi dòng thì tải lại. Lỗi 404/410 do Sheet xử lý. */
export function useReceptionVisitSummary(queueEntryId: string) {
  return useReceptionLoader(`visit-summary:${queueEntryId}`, (signal) =>
    getReceptionVisitSummary(queueEntryId, signal),
  );
}
