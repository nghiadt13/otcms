import { ROLE_LABELS, type StaffRole } from "@/lib/domain/clinicRoles";
import type {
  ActivityAction,
  ActivityCategory,
  ActivityRecordType,
} from "../types/activityLog";

/** Nhãn nhóm cho bộ lọc "Loại hành động". */
export const CATEGORY_FILTER_LABELS: Record<ActivityCategory, string> = {
  ACCOUNT: "Tài khoản",
  STAFF_PROFILE: "Hồ sơ nhân viên",
  STAFF_SHIFT: "Ca làm việc",
  ROOM: "Phòng",
  SERVICE: "Dịch vụ",
  CONTENT: "Nội dung",
  ANNOUNCEMENT: "Thông báo",
  REVIEW: "Đánh giá",
  PAYMENT: "Thanh toán",
  RESULT_CORRECTION: "Sửa kết quả",
};

export const RECORD_TYPE_LABELS: Record<ActivityRecordType, string> = {
  ACCOUNT: "Tài khoản",
  STAFF_PROFILE: "Hồ sơ nhân viên",
  STAFF_SHIFT: "Ca làm việc",
  ROOM: "Phòng",
  SERVICE: "Dịch vụ",
  CONTENT: "Nội dung",
  ANNOUNCEMENT: "Thông báo",
  REVIEW: "Đánh giá",
  PAYMENT: "Thanh toán",
  CLINICAL_RESULT: "Kết quả cận lâm sàng",
};

/** `{role}` được thay bằng nhãn vai trò, bỏ nếu không có. */
const ACTION_TEMPLATES: Record<ActivityAction, string> = {
  CREATE_ACCOUNT: "Tạo tài khoản {role}",
  DEACTIVATE_ACCOUNT: "Vô hiệu hóa tài khoản",
  REACTIVATE_ACCOUNT: "Mở lại tài khoản",
  CHANGE_ACCOUNT_ROLE: "Đổi vai trò thành {role}",
  CHANGE_ACCOUNT_EMAIL: "Đổi email tài khoản",
  REISSUE_PASSWORD: "Cấp lại mật khẩu",
  CREATE_STAFF_PROFILE: "Tạo hồ sơ nhân viên",
  UPDATE_STAFF_PROFILE: "Sửa hồ sơ nhân viên",
  CREATE_STAFF_SHIFT: "Tạo ca làm việc",
  UPDATE_STAFF_SHIFT: "Sửa ca làm việc",
  CANCEL_STAFF_SHIFT: "Hủy ca làm việc",
  CREATE_ROOM: "Tạo phòng",
  UPDATE_ROOM: "Sửa phòng",
  CHANGE_ROOM_STATUS: "Đổi trạng thái phòng",
  CREATE_SERVICE: "Tạo dịch vụ",
  UPDATE_SERVICE: "Sửa dịch vụ",
  CHANGE_SERVICE_STATUS: "Đổi trạng thái dịch vụ",
  UPDATE_CONTENT: "Sửa nội dung website",
  CREATE_ANNOUNCEMENT: "Tạo thông báo",
  UPDATE_ANNOUNCEMENT: "Sửa thông báo",
  HIDE_REVIEW: "Ẩn đánh giá",
  SHOW_REVIEW: "Hiện lại đánh giá",
  RECORD_PAYMENT: "Ghi nhận thanh toán",
  CORRECT_APPROVED_RESULT: "Sửa kết quả đã duyệt",
};

const FALLBACK_ACTION = "Thao tác khác";

/** Mã lạ (BE thêm mới) hiện "Thao tác khác", không bao giờ lộ mã. */
export function formatActivityAction(
  action: string,
  actionRole: StaffRole | null,
): string {
  const template = (ACTION_TEMPLATES as Record<string, string | undefined>)[
    action
  ];
  if (!template) return FALLBACK_ACTION;
  return template
    .replace("{role}", actionRole ? ROLE_LABELS[actionRole] : "")
    .trim();
}

export function formatActivityRecord(
  recordType: string,
  recordLabel: string,
): string {
  const type = (RECORD_TYPE_LABELS as Record<string, string | undefined>)[
    recordType
  ];
  return type ? `${type} · ${recordLabel}` : recordLabel;
}

export function formatActorRole(role: string): string {
  return (ROLE_LABELS as Record<string, string | undefined>)[role] ?? "";
}
