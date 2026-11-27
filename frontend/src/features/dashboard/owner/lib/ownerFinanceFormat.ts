import type { RefundRow, RefundStatus } from "../types/ownerFinance";

/** Nhãn vai trò theo `accounts.account_type` mà BE trả về. */
const ROLE_LABEL: Record<string, string> = {
  RECEPTIONIST: "Lễ tân",
  ADMIN: "Quản trị viên",
  OWNER: "Chủ phòng khám",
  DOCTOR: "Bác sĩ",
  LAB_TECHNICIAN: "KTV xét nghiệm",
  IMAGING_TECHNICIAN: "KTV chẩn đoán hình ảnh",
};

export function roleLabel(role: string | null): string {
  return role ? (ROLE_LABEL[role] ?? role) : "";
}

export const UNKNOWN_PERSON = "Không rõ người hoàn";

export type RefundStatusFilter = "ALL" | RefundStatus;

/** Khóa người hoàn dùng cho bộ lọc: tên (hoặc "không rõ") kèm vai trò để hai người trùng tên vẫn tách được. */
export function personKey(name: string | null, role: string | null): string {
  return `${name ?? UNKNOWN_PERSON}|${role ?? ""}`;
}

/**
 * Lọc danh sách hoàn tiền theo trạng thái và người xác nhận. `person = null` là mọi người;
 * khoản đang chờ hoàn chưa có người hoàn nên không khớp bất kỳ người nào.
 */
export function filterRefunds(
  rows: RefundRow[],
  status: RefundStatusFilter,
  person: string | null,
): RefundRow[] {
  return rows.filter(
    (row) =>
      (status === "ALL" || row.status === status) &&
      (person === null ||
        (row.status === "REFUNDED" &&
          personKey(row.refundedByName, row.refundedByRole) === person)),
  );
}
