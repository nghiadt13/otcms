import type { ActorRole, StaffRole } from "@/lib/domain/clinicRoles";
import type {
  ActivityAction,
  ActivityCategory,
  ActivityLogDetail,
  ActivityRecordType,
} from "../types/activityLog";

/** Mã hành động → nhóm lọc (BE làm việc này ở bản thật). */
export const ACTION_CATEGORY: Record<ActivityAction, ActivityCategory> = {
  CREATE_ACCOUNT: "ACCOUNT",
  DEACTIVATE_ACCOUNT: "ACCOUNT",
  REACTIVATE_ACCOUNT: "ACCOUNT",
  CHANGE_ACCOUNT_ROLE: "ACCOUNT",
  CHANGE_ACCOUNT_EMAIL: "ACCOUNT",
  REISSUE_PASSWORD: "ACCOUNT",
  CREATE_STAFF_PROFILE: "STAFF_PROFILE",
  UPDATE_STAFF_PROFILE: "STAFF_PROFILE",
  CREATE_STAFF_SHIFT: "STAFF_SHIFT",
  UPDATE_STAFF_SHIFT: "STAFF_SHIFT",
  CANCEL_STAFF_SHIFT: "STAFF_SHIFT",
  CREATE_ROOM: "ROOM",
  UPDATE_ROOM: "ROOM",
  CHANGE_ROOM_STATUS: "ROOM",
  CREATE_SERVICE: "SERVICE",
  UPDATE_SERVICE: "SERVICE",
  CHANGE_SERVICE_STATUS: "SERVICE",
  UPDATE_CONTENT: "CONTENT",
  CREATE_ANNOUNCEMENT: "ANNOUNCEMENT",
  UPDATE_ANNOUNCEMENT: "ANNOUNCEMENT",
  HIDE_REVIEW: "REVIEW",
  SHOW_REVIEW: "REVIEW",
  RECORD_PAYMENT: "PAYMENT",
  CORRECT_APPROVED_RESULT: "RESULT_CORRECTION",
};

const ACTIONS = Object.keys(ACTION_CATEGORY) as ActivityAction[];

const ACTORS: { username: string; role: ActorRole }[] = [
  { username: "admin.huy", role: "ADMIN" },
  { username: "chu.phongkham", role: "CLINIC_OWNER" },
  { username: "letan.an", role: "RECEPTIONIST" },
  { username: "bsc.minh", role: "ORTHOPEDIC_DOCTOR" },
  { username: "ktv.hinhanh.lan", role: "IMAGING_TECHNICIAN" },
  { username: "ktv.xetnghiem.tu", role: "LAB_TECHNICIAN" },
];

const STAFF_ROLE_CYCLE: StaffRole[] = [
  "RECEPTIONIST",
  "ORTHOPEDIC_DOCTOR",
  "IMAGING_TECHNICIAN",
  "LAB_TECHNICIAN",
];

const RECORD_LABELS: Record<ActivityRecordType, string[]> = {
  ACCOUNT: ["letan.binh", "bsc.thao", "ktv.xetnghiem.hoa", "bn.nguyenvana"],
  STAFF_PROFILE: ["BS. Trần Minh", "KTV. Lê Lan", "Lễ tân Phạm An"],
  STAFF_SHIFT: ["Ca sáng 21/10", "Ca chiều 22/10", "Ca sáng 23/10"],
  ROOM: ["Phòng khám 1", "Phòng X-quang", "Phòng xét nghiệm"],
  SERVICE: ["Khám cơ xương khớp", "Chụp X-quang gối", "Xét nghiệm máu"],
  CONTENT: ["Trang giới thiệu", "Giờ làm việc"],
  ANNOUNCEMENT: ["Lịch nghỉ lễ", "Thay đổi giờ khám"],
  REVIEW: ["Đánh giá của Nguyễn Văn A", "Đánh giá của Trần Thị B"],
  PAYMENT: ["HD-20261019-007", "HD-20261020-002"],
  CLINICAL_RESULT: ["XN-20261019-004", "XN-20261018-011", "CDHA-20261017-003"],
};

const REASONS = [
  "Sai ngày lấy mẫu trên phiếu, đã đối chiếu sổ",
  "Nhập nhầm đơn vị, đã xác nhận lại với kỹ thuật viên",
  "Gắn nhầm phiếu cho lượt khám khác, đã đính chính",
  "Bổ sung ghi chú của người thực hiện sau khi duyệt",
];

/** id cố định; hậu tố 12 chữ số = số thứ tự. */
export const mockLogId = (n: number) =>
  `3f9c2a10-6d1e-4b7a-9a3e-${String(n).padStart(12, "0")}`;

export const MOCK_LOG_COUNT = 134;
export const MOCK_NOT_FOUND_ID = mockLogId(404);
export const MOCK_SERVER_ERROR_ID = mockLogId(500);

/** ISO có offset +07:00 từ mốc thời gian. */
function toVietnamIso(ms: number): string {
  return new Date(ms + 7 * 3600_000).toISOString().slice(0, 19) + "+07:00";
}

/**
 * Sinh 134 mục, mới nhất trước (mỗi mục cách nhau 64 phút nên trải khoảng 6 ngày
 * tính từ `now`). Dựng lười ở API để `vi.setSystemTime` trong test có tác dụng.
 */
export function buildActivityLogSeed(now = Date.now()): ActivityLogDetail[] {
  return Array.from({ length: MOCK_LOG_COUNT }, (_, i) => {
    const action = ACTIONS[i % ACTIONS.length];
    const category = ACTION_CATEGORY[action];
    const recordType: ActivityRecordType =
      category === "RESULT_CORRECTION" ? "CLINICAL_RESULT" : category;
    const labels = RECORD_LABELS[recordType];
    const actor = ACTORS[(i * 5 + 1) % ACTORS.length];
    const needsRole =
      action === "CREATE_ACCOUNT" || action === "CHANGE_ACCOUNT_ROLE";
    return {
      id: mockLogId(i + 1),
      occurredAt: toVietnamIso(now - (i * 64 + 7) * 60_000 - (i % 60) * 1000),
      actorUsername: actor.username,
      actorRole: actor.role,
      action,
      actionRole: needsRole
        ? STAFF_ROLE_CYCLE[i % STAFF_ROLE_CYCLE.length]
        : null,
      recordType,
      recordLabel: labels[(i + Math.floor(i / ACTIONS.length)) % labels.length],
      correctionReason:
        action === "CORRECT_APPROVED_RESULT"
          ? REASONS[i % REASONS.length]
          : null,
      allowedActions: ["view"],
      recordCode: `${recordType.slice(0, 3)}-${String(1000 + i * 7).padStart(6, "0")}`,
      systemNote:
        i === 23 ? "Kết quả đã duyệt, được mở sửa theo quy trình." : null,
    };
  });
}
