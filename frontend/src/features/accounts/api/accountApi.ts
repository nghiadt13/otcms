import { apiRequest } from "@/lib/api/client";
import { toQueryString, type PageResponse } from "@/lib/api/pagination";
import type {
  AccountAction,
  AccountDetail,
  AccountListItem,
  AccountListQuery,
  AccountScope,
  AccountStatus,
  CreateAccountInput,
  IssuedCredential,
  StaffRole,
} from "../types/account";

// Bệnh nhân: /api/v1/admin/accounts (BE trả đúng dạng AccountListItem/AccountDetail).
// Nhân viên: /api/v1/admin/staff-accounts (KAN-27), dạng riêng nên chuyển ở `fromStaff*`.

type StaffItem = {
  accountId: string;
  username: string;
  role: StaffRole;
  status: AccountStatus;
  mustChangePassword: boolean;
  createdAt: string;
  staffProfile: { id: string; displayName: string } | null;
};

type StaffCreated = { accountId: string; username: string };
type StaffReissued = { accountId: string; temporaryPassword: string };

/** BE nhân viên không trả `allowedActions`; suy từ trạng thái và hồ sơ như BE bệnh nhân. */
function staffActions(item: StaffItem): AccountAction[] {
  return [
    item.status === "ACTIVE" ? "deactivate" : "activate",
    "reissuePassword",
    ...(item.staffProfile ? [] : (["createStaffProfile"] as const)),
  ];
}

/** Danh sách không mang `staffProfile` (chỉ có ở chi tiết). */
function fromStaffListItem(item: StaffItem): AccountListItem {
  return {
    id: item.accountId,
    accountType: item.role,
    username: item.username,
    email: null,
    fullName: item.staffProfile?.displayName ?? null,
    status: item.status,
    createdAt: item.createdAt,
    allowedActions: staffActions(item),
  };
}

function fromStaffItem(item: StaffItem): AccountDetail {
  return {
    ...fromStaffListItem(item),
    staffProfile: item.staffProfile
      ? {
          fullName: item.staffProfile.displayName,
          role: item.role,
          workContact: null,
        }
      : null,
  };
}

const resource = (scope: AccountScope) =>
  scope === "STAFF" ? "staff-accounts" : "accounts";

const accountPath = (scope: AccountScope, id: string) =>
  `/api/v1/admin/${resource(scope)}/${encodeURIComponent(id)}` as const;

export async function listAccounts(
  query: AccountListQuery,
  signal?: AbortSignal,
): Promise<PageResponse<AccountListItem>> {
  const scope = query.scope ?? "PATIENT";
  const path = `/api/v1/admin/${resource(scope)}${toQueryString({
    q: query.q,
    status: query.status,
    role: scope === "STAFF" ? query.role : undefined,
    page: query.page ?? 0,
    size: query.size ?? 20,
  })}` as const;
  if (scope === "PATIENT")
    return apiRequest<PageResponse<AccountListItem>>(path, { signal });
  const page = await apiRequest<PageResponse<StaffItem>>(path, { signal });
  return { ...page, items: page.items.map(fromStaffListItem) };
}

export async function getAccount(
  id: string,
  scope: AccountScope,
): Promise<AccountDetail> {
  const detail = await apiRequest<AccountDetail | StaffItem>(
    accountPath(scope, id),
  );
  return scope === "STAFF"
    ? fromStaffItem(detail as StaffItem)
    : (detail as AccountDetail);
}

/** BE chỉ trả id và tên đăng nhập; mật khẩu là giá trị Admin vừa nhập. */
export async function createAccount(
  input: CreateAccountInput,
): Promise<IssuedCredential> {
  const created = await apiRequest<StaffCreated>(
    "/api/v1/admin/staff-accounts",
    { method: "POST", body: JSON.stringify(input) },
  );
  return {
    id: created.accountId,
    username: created.username,
    initialPassword: input.initialPassword,
  };
}

async function changeStatus(
  id: string,
  scope: AccountScope,
  action: "activation" | "deactivation",
): Promise<AccountDetail> {
  const detail = await apiRequest<AccountDetail | StaffItem>(
    `${accountPath(scope, id)}/${action}`,
    { method: "POST" },
  );
  return scope === "STAFF"
    ? fromStaffItem(detail as StaffItem)
    : (detail as AccountDetail);
}

export const activateAccount = (id: string, scope: AccountScope) =>
  changeStatus(id, scope, "activation");
export const deactivateAccount = (id: string, scope: AccountScope) =>
  changeStatus(id, scope, "deactivation");

/** Chỉ nhân viên; bệnh nhân tự khôi phục bằng OTP. `username` để hiện lại trong bước kết quả. */
export async function reissueAccountPassword(
  id: string,
  username: string,
): Promise<IssuedCredential> {
  const reissued = await apiRequest<StaffReissued>(
    `${accountPath("STAFF", id)}/password-reissues`,
    { method: "POST" },
  );
  return {
    id: reissued.accountId,
    username,
    initialPassword: reissued.temporaryPassword,
  };
}
