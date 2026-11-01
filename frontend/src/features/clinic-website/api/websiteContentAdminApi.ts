import { apiRequest } from "@/lib/api/client";
import { apiBaseUrl } from "@/lib/api/apiBaseUrl";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import type {
  WebsiteContentCreatePayload,
  WebsiteContentDetail,
  WebsiteContentImageResult,
  WebsiteContentListItem,
  WebsiteContentListQuery,
  WebsiteContentUpdatePayload,
} from "../types/websiteContentAdmin";
import { rethrowContentError } from "./contentApiError";
export const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
export const ALLOWED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
const root = "/api/v1/admin/website-contents" as const;
const imageUrl = (url: string) =>
  url.startsWith("/api/") ? `${apiBaseUrl}${url}` : url;
function detail(value: WebsiteContentDetail): WebsiteContentDetail {
  return value.type === "HEALTH_ARTICLE" && value.imageUrl
    ? { ...value, imageUrl: imageUrl(value.imageUrl) }
    : value;
}
export function toListItem(
  value: WebsiteContentDetail,
): WebsiteContentListItem {
  const {
    id,
    type,
    title,
    publishedAt,
    updatedAt,
    publishedBy,
    allowedActions,
  } = value;
  return {
    id,
    type,
    title,
    publishedAt,
    updatedAt,
    publishedBy,
    allowedActions,
  };
}
export function listWebsiteContents(
  query: WebsiteContentListQuery & PageQuery = {},
  signal?: AbortSignal,
): Promise<PageResponse<WebsiteContentListItem>> {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query))
    if (value !== undefined && value !== "") params.set(key, String(value));
  return apiRequest<PageResponse<WebsiteContentListItem>>(`${root}?${params}`, {
    signal,
  }).catch(rethrowContentError);
}
export function getWebsiteContent(
  id: string,
  signal?: AbortSignal,
): Promise<WebsiteContentDetail> {
  return apiRequest<WebsiteContentDetail>(`${root}/${encodeURIComponent(id)}`, {
    signal,
  })
    .then(detail)
    .catch(rethrowContentError);
}
export function createWebsiteContent(
  input: WebsiteContentCreatePayload,
): Promise<WebsiteContentDetail> {
  return apiRequest<WebsiteContentDetail>(root, {
    method: "POST",
    body: JSON.stringify(input),
  })
    .then(detail)
    .catch(rethrowContentError);
}
export function updateWebsiteContent(
  id: string,
  input: WebsiteContentUpdatePayload,
): Promise<WebsiteContentDetail> {
  return apiRequest<WebsiteContentDetail>(`${root}/${encodeURIComponent(id)}`, {
    method: "PUT",
    body: JSON.stringify(input),
  })
    .then(detail)
    .catch(rethrowContentError);
}
export async function uploadWebsiteImage(
  file: File,
): Promise<WebsiteContentImageResult> {
  const data = new FormData();
  data.append("file", file);
  const result = await apiRequest<WebsiteContentImageResult>(`${root}/images`, {
    method: "POST",
    body: data,
  }).catch(rethrowContentError);
  return { url: imageUrl(result.url) };
}
