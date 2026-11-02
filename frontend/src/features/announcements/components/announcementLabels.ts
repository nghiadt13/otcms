import type { StatusTone } from "@/components/admin/StatusBadge";
import { ROLE_LABELS } from "@/lib/domain/clinicRoles";
import type {
  AnnouncementAudience,
  AnnouncementChannel,
  AnnouncementDeliveryStatus,
} from "../types/announcement";

export const AUDIENCE_LABELS: Record<AnnouncementAudience, string> = {
  PUBLIC: "Công khai",
  PATIENT: ROLE_LABELS.PATIENT,
  RECEPTIONIST: ROLE_LABELS.RECEPTIONIST,
  ORTHOPEDIC_DOCTOR: ROLE_LABELS.ORTHOPEDIC_DOCTOR,
  IMAGING_TECHNICIAN: ROLE_LABELS.IMAGING_TECHNICIAN,
  LAB_TECHNICIAN: ROLE_LABELS.LAB_TECHNICIAN,
};

export const CHANNEL_LABELS: Record<AnnouncementChannel, string> = {
  EMAIL: "Email",
  IN_APP: "Trong hệ thống",
};

export const DELIVERY_STATUS_VIEW: Record<
  AnnouncementDeliveryStatus,
  { label: string; tone: StatusTone }
> = {
  SENT: { label: "Đã gửi", tone: "success" },
  PARTIAL_FAILURE: { label: "Một phần lỗi", tone: "warning" },
  NOT_SUPPORTED: { label: "Chưa hỗ trợ", tone: "neutral" },
};

export const audienceLabel = (code: AnnouncementAudience) =>
  AUDIENCE_LABELS[code] ?? code;

export const channelsText = (channels: AnnouncementChannel[], joiner = ", ") =>
  channels.map((c) => CHANNEL_LABELS[c] ?? c).join(joiner);
