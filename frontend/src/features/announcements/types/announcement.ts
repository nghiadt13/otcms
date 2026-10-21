import type { PageQuery } from "@/lib/api/pagination";
import type { StaffRole } from "@/lib/domain/clinicRoles";

/** PUBLIC = Guest + Bệnh nhân (trang thông tin); PATIENT; còn lại = vai trò nhân viên. */
export type AnnouncementAudience = "PUBLIC" | "PATIENT" | StaffRole;
export type AnnouncementChannel = "EMAIL" | "IN_APP";
export type AnnouncementDeliveryStatus =
  | "SENT"
  | "PARTIAL_FAILURE"
  | "NOT_SUPPORTED";
export type AnnouncementRowAction = "view";
export type AnnouncementPageAction = "create";

export interface AnnouncementListItem {
  id: string;
  title: string;
  audience: AnnouncementAudience;
  channels: AnnouncementChannel[];
  /** ISO có offset */
  publishedAt: string;
  /** Field vắng = BE chưa trả; null = chưa có kết quả. */
  deliveryStatus?: AnnouncementDeliveryStatus | null;
  allowedActions: AnnouncementRowAction[];
}

export interface AnnouncementDetail extends AnnouncementListItem {
  body: string;
  publishedByName: string;
}

export interface AnnouncementOptions {
  audiences: AnnouncementAudience[];
  channels: AnnouncementChannel[];
  allowedActions: AnnouncementPageAction[];
}

export interface CreateAnnouncementRequest {
  title: string;
  body: string;
  audience: AnnouncementAudience;
  channels: AnnouncementChannel[];
}

/** State form: chưa chọn thì rỗng; không gửi thẳng lên API. */
export interface AnnouncementDraft {
  title: string;
  body: string;
  audience: AnnouncementAudience | "";
  channels: AnnouncementChannel[];
}

export interface AnnouncementFilters {
  q: string;
  audience: AnnouncementAudience | "";
  /** YYYY-MM-DD hoặc "" */
  from: string;
  to: string;
}

export type AnnouncementListQuery = AnnouncementFilters & PageQuery;
