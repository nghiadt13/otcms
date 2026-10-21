import type { WebsiteContentListItem } from "../types/websiteContentAdmin";

/**
 * Đường dẫn công khai của một nội dung (đề xuất, chờ chốt với phase public):
 * Thông tin phòng khám hiện ở trang chủ, Trang giới thiệu `/gioi-thieu`, Bài viết `/bai-viet/{id}`.
 */
export function websiteContentPublicPath(
  item: Pick<WebsiteContentListItem, "id" | "type">,
): string {
  if (item.type === "CLINIC_INFO") return "/";
  if (item.type === "ABOUT_PAGE") return "/gioi-thieu";
  return `/bai-viet/${item.id}`;
}
