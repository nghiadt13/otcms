import { apiRequest } from "@/lib/api/client";
import type { PageResponse } from "@/lib/api/pagination";
import type {
  AnnouncementDetail,
  AnnouncementListItem,
  AnnouncementListQuery,
  AnnouncementOptions,
  CreateAnnouncementRequest,
} from "../types/announcement";
import { rethrowAnnouncementError } from "./announcementApiError";
const root = "/api/v1/admin/announcements" as const;
export function listAnnouncements(
  query: AnnouncementListQuery,
  signal?: AbortSignal,
): Promise<PageResponse<AnnouncementListItem>> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query))
    if (value !== undefined && value !== "") params.set(key, String(value));
  return apiRequest<PageResponse<AnnouncementListItem>>(`${root}?${params}`, {
    signal,
  }).catch(rethrowAnnouncementError);
}
export function getAnnouncementOptions(
  signal?: AbortSignal,
): Promise<AnnouncementOptions> {
  return apiRequest<AnnouncementOptions>(`${root}/options`, { signal }).catch(
    rethrowAnnouncementError,
  );
}
export function createAnnouncement(
  input: CreateAnnouncementRequest,
): Promise<AnnouncementDetail> {
  return apiRequest<AnnouncementDetail>(root, {
    method: "POST",
    body: JSON.stringify(input),
  }).catch(rethrowAnnouncementError);
}
export function getAnnouncement(
  id: string,
  signal?: AbortSignal,
): Promise<AnnouncementDetail> {
  return apiRequest<AnnouncementDetail>(`${root}/${encodeURIComponent(id)}`, {
    signal,
  }).catch(rethrowAnnouncementError);
}
