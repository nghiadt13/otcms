import { ApiError, type ApiFieldError } from "@/lib/api/apiError";
import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageQuery, PageResponse } from "@/lib/api/pagination";
import type {
  WebsiteContentCreatePayload,
  WebsiteContentDetail,
  WebsiteContentImageResult,
  WebsiteContentListItem,
  WebsiteContentListQuery,
  WebsiteContentUpdatePayload,
} from "../types/websiteContentAdmin";
import {
  MOCK_ADMIN_NAME,
  NOT_FOUND_CONTENT_ID,
  SERVICE_ERROR_QUERY,
  SERVICE_ERROR_TITLE,
  websiteContentStore,
} from "./websiteContentAdminMockData";

// LỚP API GIẢ: chữ ký khớp REST contract (phase-09 mục 7). Khi có backend, thay thân
// hàm bằng apiRequest (`@/lib/api/client`; upload truyền body FormData) và giữ chữ ký.
//   GET  /api/v1/admin/website-contents        GET/PUT .../{id}
//   POST /api/v1/admin/website-contents        POST .../images (multipart, trường `file`)
// Lỗi thử trên màn: q = "loi-dich-vu" (500); tiêu đề "Lỗi dịch vụ" khi lưu (500);
// id ...0404 (404); tiêu đề/nội dung rỗng (400 MSG-69); tệp ảnh sai định dạng (400).

const READ_MS = 300;
const WRITE_MS = 400;
export const MAX_IMAGE_BYTES = 5 * 1024 * 1024;
export const ALLOWED_IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];

const nowIso = () => new Date().toISOString().replace(/\.\d{3}Z$/, "+00:00");

export function toListItem(detail: WebsiteContentDetail): WebsiteContentListItem {
  return {
    id: detail.id,
    type: detail.type,
    title: detail.title,
    publishedAt: detail.publishedAt,
    updatedAt: detail.updatedAt,
    publishedBy: detail.publishedBy,
    allowedActions: [...detail.allowedActions],
  };
}

export async function listWebsiteContents(
  query: WebsiteContentListQuery & PageQuery = {},
  signal?: AbortSignal,
): Promise<PageResponse<WebsiteContentListItem>> {
  await mockDelay(READ_MS, signal);
  const q = query.q?.trim().toLowerCase() ?? "";
  if (q === SERVICE_ERROR_QUERY) mockFail(500, "MSG-04");
  const items = websiteContentStore()
    .filter((item) => !query.type || item.type === query.type)
    .filter((item) => !q || item.title.toLowerCase().includes(q))
    .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
    .map(toListItem);
  return paginate(items, query);
}

export async function getWebsiteContent(
  id: string,
  signal?: AbortSignal,
): Promise<WebsiteContentDetail> {
  await mockDelay(READ_MS, signal);
  const found = websiteContentStore().find((item) => item.id === id);
  if (id === NOT_FOUND_CONTENT_ID || !found) mockFail(404, "MSG-50");
  return clone(found);
}

// Kiểm tra phía "BE": thiếu tiêu đề/nội dung → 400 MSG-69 kèm fieldErrors.
function assertPublishable(title: string, body: WebsiteContentCreatePayload["body"]) {
  const fieldErrors: ApiFieldError[] = [];
  if (!title.trim()) fieldErrors.push({ field: "title", code: "MSG-69" });
  if (typeof body === "string") {
    if (!body.trim()) fieldErrors.push({ field: "body", code: "MSG-69" });
  } else {
    for (const key of ["clinicName", "address", "hotline"] as const)
      if (!body[key].trim())
        fieldErrors.push({ field: `body.${key}`, code: "MSG-69" });
  }
  if (fieldErrors.length > 0) mockFail(400, "MSG-69", undefined, fieldErrors);
  if (title.trim() === SERVICE_ERROR_TITLE) mockFail(500, "MSG-04");
}

export async function createWebsiteContent(
  input: WebsiteContentCreatePayload,
): Promise<WebsiteContentDetail> {
  await mockDelay(WRITE_MS);
  assertPublishable(input.title, input.body);
  const now = nowIso();
  const created = {
    ...clone(input),
    id: crypto.randomUUID(),
    title: input.title.trim(),
    publishedAt: now,
    updatedAt: now,
    publishedBy: MOCK_ADMIN_NAME,
    allowedActions: ["edit", "viewOnWebsite"],
  } as WebsiteContentDetail;
  websiteContentStore().unshift(created);
  return clone(created);
}

export async function updateWebsiteContent(
  id: string,
  input: WebsiteContentUpdatePayload,
): Promise<WebsiteContentDetail> {
  await mockDelay(WRITE_MS);
  const index = websiteContentStore().findIndex((item) => item.id === id);
  if (id === NOT_FOUND_CONTENT_ID || index < 0) mockFail(404, "MSG-50");
  assertPublishable(input.title, input.body);
  const current = websiteContentStore()[index];
  // Giữ id, loại, ngày và người đăng lần đầu; chỉ đổi nội dung và `updatedAt`.
  const next = {
    ...current,
    title: input.title.trim(),
    body: clone(input.body),
    ...(current.type === "HEALTH_ARTICLE" ? { imageUrl: input.imageUrl ?? null } : {}),
    updatedAt: nowIso(),
  } as WebsiteContentDetail;
  websiteContentStore()[index] = next;
  return clone(next);
}

export async function uploadWebsiteImage(
  file: File,
): Promise<WebsiteContentImageResult> {
  await mockDelay(WRITE_MS);
  if (!ALLOWED_IMAGE_TYPES.includes(file.type) || file.size > MAX_IMAGE_BYTES)
    throw new ApiError(400, {
      code: "MSG-69",
      fieldErrors: [{ field: "file", code: "MSG-69" }],
    });
  const extension =
    file.type === "image/png" ? "png" : file.type === "image/webp" ? "webp" : "jpg";
  return {
    url: `/media/website/2026/10/${crypto.randomUUID().slice(0, 8)}.${extension}`,
  };
}
