import { apiRequest } from "@/lib/api/client";
import { toQueryString, type PageQuery, type PageResponse } from "@/lib/api/pagination";
import type { PublicAnnouncement } from "../types/publicAnnouncement";
export function listPatientAnnouncements(query: PageQuery = {}, signal?: AbortSignal): Promise<PageResponse<PublicAnnouncement>> {
  return apiRequest(`/api/v1/me/announcements${toQueryString(query)}`, { signal });
}
