import { publicApiRequest } from "@/lib/api/client";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import type { PublicAnnouncement } from "../types/publicAnnouncement";
import { rethrowAnnouncementError } from "./announcementApiError";
const root = "/api/v1/public/announcements" as const;
export function listPublicAnnouncements(
  query: PageQuery = {},
  signal?: AbortSignal,
): Promise<PageResponse<PublicAnnouncement>> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query))
    if (value !== undefined) params.set(key, String(value));
  return publicApiRequest<PageResponse<PublicAnnouncement>>(
    `${root}?${params}`,
    { signal },
  ).catch(rethrowAnnouncementError);
}
export function getPublicAnnouncement(
  id: string,
  signal?: AbortSignal,
): Promise<PublicAnnouncement> {
  return publicApiRequest<PublicAnnouncement>(
    `${root}/${encodeURIComponent(id)}`,
    { signal },
  ).catch(rethrowAnnouncementError);
}
