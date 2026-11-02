import type { VisitStatus } from "@/lib/domain/clinicVisit";
import { mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import { addDays, todayIsoDate } from "@/lib/format/formatDate";
import { getLinkedProfileRecords } from "@/lib/patient-scope/linkedProfilesMockStore";
import type { LinkedProfile } from "@/lib/patient-scope/types";
import type {
  VisitAction,
  VisitListQuery,
  VisitProgress,
  VisitQueuePosition,
  VisitSummary,
  VisitWhen,
} from "../types/visitProgress";
import { takeProgressRead, visitMockNow } from "./visitMockStore";
import { visitSeeds, type VisitSeed } from "./visitMockData";

// LỚP API GIẢ của lượt khám (UC34). Chữ ký khớp REST (xem api-notes/P05.md). Khi nối BE: thay thân
// từng hàm bằng apiRequest, giữ tên hàm và kiểu. Chỉ trả lượt của hồ sơ đang gắn với tài khoản
// (BR-19); lượt của hồ sơ đã bỏ gắn coi như không tồn tại (403 MSG-40 khi xem chi tiết).

const latency = (signal?: AbortSignal) =>
  mockDelay(
    process.env.NODE_ENV === "test" ? 0 : 300 + Math.random() * 200,
    signal,
  );

const ACTIVE_STATUSES: readonly VisitStatus[] = [
  "WAITING_FOR_EXAMINATION",
  "IN_EXAMINATION",
  "AWAITING_CONCLUSION",
];

/** Lượt của hồ sơ đang gắn; hồ sơ đã bỏ gắn thì không có lượt nào hiện ra. */
function profileOf(seed: VisitSeed, linked: readonly LinkedProfile[]) {
  return linked.find((profile) => profile.patientCode === seed.patientCode);
}

function summaryOf(seed: VisitSeed, profile: LinkedProfile): VisitSummary {
  return {
    id: seed.id,
    visitDate: seed.visitDate,
    status: seed.status,
    type: seed.type,
    patient: {
      id: profile.id,
      fullName: profile.fullName,
      relationship: profile.relationship,
    },
  };
}

/** Active: đang chờ hoặc đang khám. Recent: mọi trạng thái, ngày trong 30 ngày gần nhất. */
function matchesWhen(
  item: VisitSummary,
  when: VisitWhen,
  today: string,
): boolean {
  if (when === "active") return ACTIVE_STATUSES.includes(item.status);
  return item.visitDate >= addDays(today, -30) && item.visitDate <= today;
}

const newestFirst = (a: VisitSummary, b: VisitSummary) =>
  b.visitDate.localeCompare(a.visitDate) || a.id.localeCompare(b.id);

/** Hàng đợi tiến dần: mỗi lần đọc giảm 1 người phía trước; về 0 thì đang gọi. */
function queuesOf(seed: VisitSeed, reads: number): VisitQueuePosition[] {
  if (!seed.advancing) return seed.queues;
  return seed.queues.map((entry): VisitQueuePosition => {
    const peopleAhead = Math.max(0, entry.peopleAhead - reads);
    return peopleAhead === 0
      ? { ...entry, peopleAhead, status: "BEING_SERVED" }
      : { ...entry, peopleAhead };
  });
}

/** Việc BE cho phép theo trạng thái và chỉ định: BE quyết, FE chỉ vẽ nút tương ứng. */
function allowedActionsOf(seed: VisitSeed): VisitAction[] {
  const actions: VisitAction[] = [];
  if (seed.orders.some((order) => order.status === "PENDING_PAYMENT"))
    actions.push("payOrders");
  if (seed.status === "COMPLETED" || seed.status === "INCOMPLETE")
    actions.push("viewResults");
  if (seed.status === "COMPLETED") actions.push("review");
  return actions;
}

/** ISO giờ Việt Nam (+07:00, không có giờ mùa hè). */
function vnIsoOf(now: Date): string {
  const shifted = new Date(now.getTime() + 7 * 60 * 60 * 1000);
  return `${shifted.toISOString().slice(0, 19)}+07:00`;
}

function progressOf(
  seed: VisitSeed,
  profile: LinkedProfile,
  reads: number,
  now: Date,
): VisitProgress {
  return {
    ...summaryOf(seed, profile),
    updatedAt: vnIsoOf(now),
    returnDeadline: seed.returnDeadline,
    queues: queuesOf(seed, reads),
    orders: seed.orders,
    allowedActions: allowedActionsOf(seed),
  };
}

/** GET /api/v1/visits: `when`, `patientId?`, `page`, `size`. */
export async function listVisits(
  query: VisitListQuery & PageQuery,
  signal?: AbortSignal,
): Promise<PageResponse<VisitSummary>> {
  await latency(signal);
  if (query.when !== "active" && query.when !== "recent")
    mockFail(400, "MSG-31", undefined, [{ field: "when", code: "MSG-31" }]);
  const linked = getLinkedProfileRecords();
  if (
    query.patientId &&
    !linked.some((profile) => profile.id === query.patientId)
  )
    mockFail(403, "MSG-40");

  const now = visitMockNow();
  const today = todayIsoDate(now);
  const items = visitSeeds(now)
    .flatMap((seed) => {
      const profile = profileOf(seed, linked);
      return profile ? [summaryOf(seed, profile)] : [];
    })
    .filter((item) => !query.patientId || item.patient.id === query.patientId)
    .filter((item) => matchesWhen(item, query.when, today))
    .sort(newestFirst);
  return paginate(items, query);
}

/**
 * GET /api/v1/visits/{id}/progress. Lượt không có → 404 MSG-41; lượt của hồ sơ không còn gắn →
 * 403 MSG-40. Mỗi lần đọc được tính là một lần tải lại (xem queuesOf).
 */
export async function getVisitProgress(
  visitId: string,
  signal?: AbortSignal,
): Promise<VisitProgress> {
  await latency(signal);
  const now = visitMockNow();
  const seed = visitSeeds(now).find((item) => item.id === visitId);
  if (!seed) mockFail(404, "MSG-41");
  const profile = profileOf(seed, getLinkedProfileRecords());
  if (!profile) mockFail(403, "MSG-40");
  return progressOf(seed, profile, takeProgressRead(seed.id), now);
}
