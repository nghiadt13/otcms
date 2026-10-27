import { clone, mockDelay, mockFail, paginate } from "@/lib/api/mockServer";
import type { PageResponse } from "@/lib/api/pagination";
import { STAFF_ROLES } from "@/lib/domain/clinicRoles";
import {
  generateInitialPassword,
  validateInitialPassword,
  validateUsername,
} from "../schema/accountFieldRules";
import type {
  AccountDetail,
  AccountListItem,
  AccountListQuery,
  CreateAccountInput,
  IssuedCredential,
} from "../types/account";
import { accountStore, computeAllowedActions } from "./accountMockData";

// Lớp API giả cho /api/v1/admin/accounts. Chữ ký khớp phase-02 mục 7: khi nối BE thật
// chỉ đổi thân hàm sang `apiRequest`. Mật khẩu khởi tạo chỉ có trong phản hồi tạo/cấp lại.

const MAX_QUERY_LENGTH = 100;
const DELAY_MS = 120;

// Danh sách không mang `staffProfile` (chỉ có ở chi tiết).
const toListItem = (account: AccountDetail): AccountListItem => {
  const item: Partial<AccountDetail> = { ...account };
  delete item.staffProfile;
  return item as AccountListItem;
};

function findOrFail(id: string): AccountDetail {
  const found = accountStore().find((account) => account.id === id);
  // Mã 404 chờ chốt (MSG-14 theo spec 02, MSG-50 theo phase-00): mock dùng MSG-14.
  return found ?? mockFail(404, "MSG-14");
}

function refreshActions(account: AccountDetail) {
  account.allowedActions = computeAllowedActions(account);
}

export async function listAccounts(
  query: AccountListQuery,
  signal?: AbortSignal,
): Promise<PageResponse<AccountListItem>> {
  await mockDelay(DELAY_MS, signal);
  const keyword = query.q?.trim().toLowerCase() ?? "";
  if (keyword.length > MAX_QUERY_LENGTH) mockFail(400, "MSG-54");

  const matched = accountStore()
    .filter((account) => !query.role || account.accountType === query.role)
    .filter((account) => !query.status || account.status === query.status)
    .filter(
      (account) =>
        !keyword ||
        [account.username, account.email, account.fullName].some((field) =>
          field?.toLowerCase().includes(keyword),
        ),
    )
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    .map(toListItem);
  return clone(paginate(matched, query));
}

export async function getAccount(id: string): Promise<AccountDetail> {
  await mockDelay(DELAY_MS);
  return clone(findOrFail(id));
}

export async function createAccount(
  input: CreateAccountInput,
): Promise<IssuedCredential> {
  await mockDelay(DELAY_MS);
  const username = input.username.trim();

  const fieldErrors = [
    ...(STAFF_ROLES.includes(input.role)
      ? []
      : [{ field: "role", code: "MSG-12" as const }]),
    ...(validateUsername(username)
      ? [{ field: "username", code: "MSG-12" as const }]
      : []),
    ...(validateInitialPassword(input.initialPassword)
      ? [{ field: "initialPassword", code: "MSG-12" as const }]
      : []),
  ];
  if (fieldErrors.length) mockFail(400, "MSG-12", undefined, fieldErrors);

  // Tên đăng nhập đặc biệt để thử các nhánh lỗi.
  if (username === "khong.quyen") mockFail(403, "MSG-14");
  if (username === "loi.tao") mockFail(500, "MSG-16");
  if (accountStore().some((account) => account.username === username))
    mockFail(409, "MSG-13", { username }, [
      { field: "username", code: "MSG-13" },
    ]);

  const account: AccountDetail = {
    id: crypto.randomUUID(),
    accountType: input.role,
    username,
    email: null,
    fullName: null,
    status: "ACTIVE",
    createdAt: new Date().toISOString(),
    allowedActions: [],
    staffProfile: null,
  };
  refreshActions(account);
  accountStore().push(account);
  return { id: account.id, username, initialPassword: input.initialPassword };
}

async function changeStatus(
  id: string,
  target: "ACTIVE" | "INACTIVE",
): Promise<AccountDetail> {
  await mockDelay(DELAY_MS);
  const account = findOrFail(id);
  // Đã ở trạng thái đích (ví dụ kích hoạt tài khoản đang Active): không cho phép.
  if (account.status === target) mockFail(409, "MSG-42");
  account.status = target;
  refreshActions(account);
  return clone(account);
}

export const activateAccount = (id: string) => changeStatus(id, "ACTIVE");
export const deactivateAccount = (id: string) => changeStatus(id, "INACTIVE");

export async function reissueAccountPassword(
  id: string,
): Promise<IssuedCredential> {
  await mockDelay(DELAY_MS);
  const account = findOrFail(id);
  // Bệnh nhân tự khôi phục bằng OTP email, Admin không cấp lại.
  if (account.accountType === "PATIENT" || !account.username)
    mockFail(403, "MSG-14");
  return {
    id,
    username: account.username,
    initialPassword: generateInitialPassword(),
  };
}
