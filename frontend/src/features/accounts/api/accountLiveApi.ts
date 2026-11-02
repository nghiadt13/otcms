import { apiRequest } from "@/lib/api/client";
import { isApiError } from "@/lib/api/apiError";
import { toQueryString, type PageResponse } from "@/lib/api/pagination";
import type {
  AccountDetail,
  AccountListItem,
  AccountListQuery,
} from "../types/account";
import type { StaffRole } from "@/lib/domain/clinicRoles";

type IssuedStaffAccount = {
  accountId: string;
  email: string;
  temporaryPassword: string;
};

export function createLiveStaffAccount(input: {
  email: string;
  fullName: string;
  role: StaffRole;
}): Promise<IssuedStaffAccount> {
  return apiRequest<IssuedStaffAccount>("/api/v1/admin/staff-accounts", {
    method: "POST",
    body: JSON.stringify({
      ...input,
      role: input.role === "ORTHOPEDIC_DOCTOR" ? "DOCTOR" : input.role,
    }),
  });
}

// Backend hiện có đầy đủ API bệnh nhân. Nhóm nhân viên dùng cùng hợp đồng response,
// nhưng endpoint GET/đổi trạng thái cần được backend bổ sung.
const staffIds = new Set<string>();

function resource(id: string) {
  return staffIds.has(id) ? "staff-accounts" : "accounts";
}

export async function listLiveAccounts(
  query: AccountListQuery,
  signal?: AbortSignal,
): Promise<PageResponse<AccountListItem>> {
  const group = query.scope === "STAFF" ? "staff-accounts" : "accounts";
  const path = `/api/v1/admin/${group}${toQueryString({
    q: query.q,
    status: query.status,
    role: query.scope === "STAFF" ? query.role : undefined,
    page: query.page ?? 0,
    size: query.size ?? 20,
  })}` as const;
  let page: PageResponse<AccountListItem>;
  try {
    page = await apiRequest<PageResponse<AccountListItem>>(path, { signal });
  } catch (error) {
    if (group === "staff-accounts" && isApiError(error) && error.status === 404)
      throw new Error(
        "Backend chưa có API danh sách tài khoản nhân viên. Không thể hiển thị dữ liệu thật cho tab này.",
      );
    throw error;
  }
  if (group === "staff-accounts")
    page.items.forEach((item) => staffIds.add(item.id));
  return page;
}

export function getLiveAccount(id: string): Promise<AccountDetail> {
  return apiRequest<AccountDetail>(
    `/api/v1/admin/${resource(id)}/${encodeURIComponent(id)}`,
  );
}

export function changeLiveAccountStatus(
  id: string,
  action: "activation" | "deactivation",
): Promise<AccountDetail> {
  return apiRequest<AccountDetail>(
    `/api/v1/admin/${resource(id)}/${encodeURIComponent(id)}/${action}`,
    { method: "POST" },
  );
}

export function reissueLiveAccountPassword(
  id: string,
): Promise<{ id: string; username: string; initialPassword: string }> {
  return apiRequest(
    `/api/v1/admin/staff-accounts/${encodeURIComponent(id)}/password-reissues`,
    { method: "POST" },
  );
}
