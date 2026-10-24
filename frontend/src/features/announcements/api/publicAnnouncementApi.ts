import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import type { PublicAnnouncement } from "../types/publicAnnouncement";
import type { AnnouncementDetail } from "../types/announcement";
import { mockStore } from "./announcementMockData";

// LỚP API GIẢ cho /api/v1/public/announcements (không cần đăng nhập). Đọc cùng kho thông báo
// với màn Admin: chỉ thông báo có nhóm nhận "PUBLIC" mới hiện (BR-61).

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 200;

const isPublicAudience = (item: AnnouncementDetail) => item.audience === "PUBLIC";

function toPublicAnnouncement(item: AnnouncementDetail): PublicAnnouncement {
  return {
    id: item.id,
    title: item.title,
    body: item.body,
    publishedAt: item.publishedAt,
  };
}

/** GET /api/v1/public/announcements?page=&size= — mới nhất trước. */
export async function listPublicAnnouncements(
  query: PageQuery = {},
  signal?: AbortSignal,
): Promise<PageResponse<PublicAnnouncement>> {
  await mockDelay(LATENCY_MS, signal);
  const rows = mockStore.announcements
    .filter(isPublicAudience)
    .sort((a, b) => b.publishedAt.localeCompare(a.publishedAt))
    .map(toPublicAnnouncement);
  return clone(paginate(rows, query));
}

/** GET /api/v1/public/announcements/{id} — không dành cho khách: 404 MSG-50. */
export async function getPublicAnnouncement(
  id: string,
  signal?: AbortSignal,
): Promise<PublicAnnouncement> {
  await mockDelay(LATENCY_MS, signal);
  const found = mockStore.announcements.find(
    (item) => item.id === id && isPublicAudience(item),
  );
  if (!found) mockFail(404, "MSG-50");
  return clone(toPublicAnnouncement(found));
}
