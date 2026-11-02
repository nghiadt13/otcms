import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageQuery } from "@/lib/api/pagination";
import { validateActivityLogDateRange } from "../schema/activityLogFilterRules";
import type {
  ActivityLogDetail,
  ActivityLogFilters,
  ActivityLogItem,
  ActivityLogPage,
} from "../types/activityLog";
import {
  ACTION_CATEGORY,
  buildActivityLogSeed,
  MOCK_NOT_FOUND_ID,
  MOCK_SERVER_ERROR_ID,
} from "./activityLogMockData";

let store: ActivityLogDetail[] | null = null;
const getStore = () => (store ??= buildActivityLogSeed());

/** Chỉ cho test: dựng lại dữ liệu theo đồng hồ hiện tại. */
export function resetActivityLogStore() {
  store = null;
}

const includesText = (haystack: string, needle: string) =>
  haystack.toLowerCase().includes(needle.trim().toLowerCase());

function toListItem(log: ActivityLogDetail): ActivityLogItem {
  const { recordCode, systemNote, ...item } = log;
  void recordCode;
  void systemNote;
  return item;
}

/**
 * GET /api/v1/admin/activity-logs. Mới nhất trước (sort cố định), phân trang server.
 * `record` và `actor` khớp chứa, không phân biệt hoa thường.
 */
export async function listActivityLogs(
  query: ActivityLogFilters & PageQuery,
  signal?: AbortSignal,
): Promise<ActivityLogPage> {
  await mockDelay(300, signal);
  if (validateActivityLogDateRange(query.from, query.to)) {
    mockFail(400, "MSG-54", undefined, [{ field: "from", code: "MSG-54" }]);
  }
  if (query.record.trim() === "loi-may-chu") mockFail(500, "MSG-04");

  const items = getStore()
    .filter((log) => {
      const day = log.occurredAt.slice(0, 10);
      if (query.from && day < query.from) return false;
      if (query.to && day > query.to) return false;
      if (query.action && ACTION_CATEGORY[log.action] !== query.action)
        return false;
      if (query.record.trim() && !includesText(log.recordLabel, query.record))
        return false;
      if (query.actor.trim() && !includesText(log.actorUsername, query.actor))
        return false;
      return true;
    })
    .sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))
    .map(toListItem);

  const result = paginate(items, query);
  // Không có kết quả: BE trả totalPages 0.
  return clone(items.length === 0 ? { ...result, totalPages: 0 } : result);
}

/** GET /api/v1/admin/activity-logs/{id}. */
export async function getActivityLog(
  id: string,
  signal?: AbortSignal,
): Promise<ActivityLogDetail> {
  await mockDelay(300, signal);
  if (id === MOCK_NOT_FOUND_ID) mockFail(404, "MSG-50");
  if (id === MOCK_SERVER_ERROR_ID) mockFail(500, "MSG-04");
  const found = getStore().find((log) => log.id === id);
  if (!found) mockFail(404, "MSG-50");
  return clone(found);
}
