import type { ClinicContact } from "./websiteContent";

// Kiểu cho màn quản trị nội dung website (UC72–UC73). Khác `websiteContent.ts`
// (bản công khai {contact, hero}); không dùng chung tên để tránh nhầm.
export type WebsiteContentType =
  "CLINIC_INFO" | "HEALTH_ARTICLE" | "ABOUT_PAGE";
export type WebsiteContentAction = "edit" | "viewOnWebsite";

export const WEBSITE_CONTENT_TYPES: WebsiteContentType[] = [
  "CLINIC_INFO",
  "HEALTH_ARTICLE",
  "ABOUT_PAGE",
];

export const WEBSITE_CONTENT_TYPE_LABELS: Record<WebsiteContentType, string> = {
  CLINIC_INFO: "Thông tin phòng khám",
  HEALTH_ARTICLE: "Bài viết sức khỏe",
  ABOUT_PAGE: "Trang giới thiệu",
};

// Dòng danh sách, không có body.
export type WebsiteContentListItem = {
  id: string;
  type: WebsiteContentType;
  title: string;
  /** ISO có offset: ngày đăng lần đầu. */
  publishedAt: string;
  /** ISO có offset: lần lưu gần nhất. */
  updatedAt: string;
  /** Tên tài khoản Admin đăng lần đầu. */
  publishedBy: string;
  allowedActions: WebsiteContentAction[];
};

// Chi tiết mở Sheet sửa; body theo loại.
export type WebsiteContentDetail = WebsiteContentListItem &
  (
    | { type: "CLINIC_INFO"; body: ClinicContact }
    | { type: "HEALTH_ARTICLE"; body: string; imageUrl: string | null }
    | { type: "ABOUT_PAGE"; body: string }
  );

// POST có type; PUT không gửi type (loại không đổi khi sửa).
export type WebsiteContentCreatePayload =
  | { type: "CLINIC_INFO"; title: string; body: ClinicContact }
  | {
      type: "HEALTH_ARTICLE";
      title: string;
      body: string;
      imageUrl: string | null;
    }
  | { type: "ABOUT_PAGE"; title: string; body: string };

export type WebsiteContentUpdatePayload = {
  title: string;
  body: ClinicContact | string;
  /** Chỉ HEALTH_ARTICLE. */
  imageUrl?: string | null;
};

export type WebsiteContentListQuery = { type?: WebsiteContentType; q?: string };
export type WebsiteContentImageResult = { url: string };
