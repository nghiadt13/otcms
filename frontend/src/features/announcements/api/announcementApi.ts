import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageResponse } from "@/lib/api/pagination";
import type {
  AnnouncementDetail,
  AnnouncementListItem,
  AnnouncementListQuery,
  AnnouncementOptions,
  CreateAnnouncementRequest,
} from "../types/announcement";
import { mockConfig, mockStore } from "./announcementMockData";

// Chữ ký khớp REST contract (phase-10 mục 7). Hiện chạy trên kho giả.

const wait = (signal?: AbortSignal) => mockDelay(mockConfig.latencyMs, signal);

/** Ngày YYYY-MM-DD theo giờ Việt Nam của một ISO có offset +07:00. */
const vnDate = (iso: string) => iso.slice(0, 10);

function nowVnIso(): string {
  const shifted = new Date(Date.now() + 7 * 60 * 60 * 1000);
  return `${shifted.toISOString().slice(0, 19)}+07:00`;
}

function toListItem(detail: AnnouncementDetail): AnnouncementListItem {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars -- danh sách không có body, người đăng
  const { body, publishedByName, ...item } = detail;
  return item;
}

/** GET /api/v1/admin/announcements */
export async function listAnnouncements(
  query: AnnouncementListQuery,
  signal?: AbortSignal,
): Promise<PageResponse<AnnouncementListItem>> {
  await wait(signal);
  const q = query.q?.trim().toLowerCase();
  if (q === "loi-tai") mockFail(500, "MSG-04");
  if (query.from && query.to && query.from > query.to) mockFail(400, "MSG-54");
  const rows = mockStore.announcements
    .filter((a) => !q || a.title.toLowerCase().includes(q))
    .filter((a) => !query.audience || a.audience === query.audience)
    .filter((a) => !query.from || vnDate(a.publishedAt) >= query.from)
    .filter((a) => !query.to || vnDate(a.publishedAt) <= query.to)
    .sort((a, b) => b.publishedAt.localeCompare(a.publishedAt))
    .map((a) => clone(toListItem(a)));
  return paginate(rows, query);
}

/** GET /api/v1/admin/announcements/options */
export async function getAnnouncementOptions(
  signal?: AbortSignal,
): Promise<AnnouncementOptions> {
  await wait(signal);
  return clone(mockStore.options);
}

/** POST /api/v1/admin/announcements -> 201 */
export async function createAnnouncement(
  input: CreateAnnouncementRequest,
): Promise<AnnouncementDetail> {
  await wait();
  const title = input.title.trim();
  if (title === "Thử lỗi quyền") mockFail(403, "MSG-50");
  if (title === "Thử lỗi dịch vụ") mockFail(503, "MSG-04");
  if (title === "Thử lỗi nội dung")
    mockFail(400, "MSG-12", undefined, [{ field: "body", code: "MSG-71" }]);
  const { audiences, channels } = mockStore.options;
  if (!audiences.includes(input.audience))
    mockFail(400, "MSG-12", undefined, [{ field: "audience", code: "MSG-71" }]);
  if (!title)
    mockFail(400, "MSG-12", undefined, [{ field: "title", code: "MSG-12" }]);
  if (!input.body.trim())
    mockFail(400, "MSG-12", undefined, [{ field: "body", code: "MSG-71" }]);
  if (
    input.channels.length === 0 ||
    input.channels.some((c) => !channels.includes(c))
  )
    mockFail(400, "MSG-12", undefined, [{ field: "channels", code: "MSG-12" }]);

  mockStore.nextId += 1;
  const created: AnnouncementDetail = {
    id: `00000000-0000-4000-8000-${String(mockStore.nextId).padStart(12, "0")}`,
    title,
    body: input.body,
    audience: input.audience,
    channels: [...input.channels],
    publishedAt: nowVnIso(),
    publishedByName: "Nguyễn Quản Trị",
    deliveryStatus: "SENT",
    allowedActions: ["view"],
  };
  mockStore.announcements.unshift(created);
  return clone(created);
}

/** GET /api/v1/admin/announcements/{id} */
export async function getAnnouncement(
  id: string,
  signal?: AbortSignal,
): Promise<AnnouncementDetail> {
  await wait(signal);
  const found = mockStore.announcements.find((a) => a.id === id);
  if (!found) mockFail(404, "MSG-50");
  return clone(found);
}
