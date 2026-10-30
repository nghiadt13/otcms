import type { PageResponse } from "@/lib/api/pagination";
import type { ActorRole, StaffRole } from "@/lib/domain/clinicRoles";

/** Nhóm cho bộ lọc "Loại hành động". BE map mã hành động sang nhóm. */
export type ActivityCategory =
  | "ACCOUNT"
  | "STAFF_PROFILE"
  | "STAFF_SHIFT"
  | "ROOM"
  | "SERVICE"
  | "CONTENT"
  | "ANNOUNCEMENT"
  | "REVIEW"
  | "PAYMENT"
  | "RESULT_CORRECTION";

/** Đối tượng bị tác động (cột "Bản ghi"): như nhóm, trừ RESULT_CORRECTION -> CLINICAL_RESULT. */
export type ActivityRecordType =
  Exclude<ActivityCategory, "RESULT_CORRECTION"> | "CLINICAL_RESULT";

/** Mã hành động cụ thể (cột "Hành động"). Bảng nhãn: labels/activityLabels.ts. */
export type ActivityAction =
  | "CREATE_ACCOUNT"
  | "DEACTIVATE_ACCOUNT"
  | "REACTIVATE_ACCOUNT"
  | "CHANGE_ACCOUNT_ROLE"
  | "CHANGE_ACCOUNT_EMAIL"
  | "REISSUE_PASSWORD"
  | "CREATE_STAFF_PROFILE"
  | "UPDATE_STAFF_PROFILE"
  | "CREATE_STAFF_SHIFT"
  | "UPDATE_STAFF_SHIFT"
  | "CANCEL_STAFF_SHIFT"
  | "CREATE_ROOM"
  | "UPDATE_ROOM"
  | "CHANGE_ROOM_STATUS"
  | "CREATE_SERVICE"
  | "UPDATE_SERVICE"
  | "CHANGE_SERVICE_STATUS"
  | "UPDATE_CONTENT"
  | "CREATE_ANNOUNCEMENT"
  | "UPDATE_ANNOUNCEMENT"
  | "HIDE_REVIEW"
  | "SHOW_REVIEW"
  | "RECORD_PAYMENT"
  | "CORRECT_APPROVED_RESULT";

/** Không có khóa before/after/payload: nhật ký không bao giờ trả nội dung thay đổi. */
export type ActivityLogItem = {
  id: string;
  /** ISO có offset, ví dụ "2026-10-20T09:15:00+07:00". */
  occurredAt: string;
  actorUsername: string;
  actorRole: ActorRole;
  action: ActivityAction;
  /** Vai trò được tạo/đổi (CREATE_ACCOUNT, CHANGE_ACCOUNT_ROLE). */
  actionRole: StaffRole | null;
  recordType: ActivityRecordType;
  recordLabel: string;
  /** Chỉ có với CORRECT_APPROVED_RESULT. */
  correctionReason: string | null;
  allowedActions: "view"[];
};

export type ActivityLogDetail = ActivityLogItem & {
  recordCode: string | null;
  systemNote: string | null;
};

export type ActivityLogFilters = {
  /** YYYY-MM-DD */
  from: string;
  to: string;
  /** Giá trị là nhóm; null = tất cả. */
  action: ActivityCategory | null;
  record: string;
  actor: string;
};

export type ActivityLogPage = PageResponse<ActivityLogItem>;
