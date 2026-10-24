import { listPriorityGroups } from "../api/checkInApi";
import type { PriorityGroup } from "../types/checkIn";
import { useReceptionLoader } from "./useReceptionLoader";

/** Danh mục nhóm ưu tiên từ cấu hình BE (BR-31). Lỗi tải: màn cho thử lại. */
export function usePriorityGroups(): {
  groups: PriorityGroup[];
  error: unknown;
  loading: boolean;
  reload: () => void;
} {
  const { data, error, loading, reload } = useReceptionLoader("priority-groups", (signal) =>
    listPriorityGroups(signal),
  );
  return { groups: data?.items ?? [], error, loading, reload };
}
