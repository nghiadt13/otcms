// Kiểu khớp đúng JSON của /api/v1/admin/accounts (phase-02 mục 6, 7).
import type { PageQuery } from "@/lib/api/pagination";
import type { AccountType, StaffRole } from "@/lib/domain/clinicRoles";

export type { AccountType, StaffRole };

export type AccountStatus = "ACTIVE" | "INACTIVE";

/** FE chỉ vẽ nút có trong mảng này, không suy từ `status`. */
export type AccountAction =
  | "deactivate"
  | "activate"
  | "reissuePassword"
  | "createStaffProfile";

export interface AccountListItem {
  id: string;
  accountType: AccountType;
  /** Nhân viên đăng nhập bằng tên đăng nhập (không có email). */
  username: string | null;
  /** Bệnh nhân đăng nhập bằng email. */
  email: string | null;
  /** null: nhân viên chưa có hồ sơ. */
  fullName: string | null;
  status: AccountStatus;
  /** ISO có offset. */
  createdAt: string;
  allowedActions: AccountAction[];
}

export interface StaffProfileSummary {
  fullName: string;
  role: StaffRole;
  /** Chỉ đọc. */
  workContact: string | null;
}

export interface AccountDetail extends AccountListItem {
  staffProfile: StaffProfileSummary | null;
}

export interface AccountListQuery extends PageQuery {
  q?: string;
  role?: AccountType;
  status?: AccountStatus;
}

export interface CreateAccountInput {
  role: StaffRole;
  username: string;
  initialPassword: string;
}

/** Mật khẩu một lần: chỉ nằm trong state Dialog, không vào store, list, toast hay URL. */
export interface IssuedCredential {
  id: string;
  username: string;
  initialPassword: string;
}
