import type {
  AccountStatus,
  CreateStaffInput,
  StaffRole,
  UserAccount,
} from "../types/account";
import { initialAccounts } from "./accountMockData";

let accounts: UserAccount[] = initialAccounts.map((account) => ({ ...account }));

const wait = (ms = 250) => new Promise((resolve) => setTimeout(resolve, ms));

function assertUniqueEmail(email: string, ignoreId?: string) {
  const normalized = email.trim().toLowerCase();
  const duplicated = accounts.some(
    (account) =>
      account.id !== ignoreId &&
      account.email.trim().toLowerCase() === normalized,
  );
  if (duplicated) {
    // MSG-13: Email không thể dùng để tạo tài khoản mới
    throw new Error(
      "Email này không thể dùng để tạo tài khoản mới. Vui lòng chọn email khác.",
    );
  }
}

export async function listAccounts(): Promise<UserAccount[]> {
  await wait();
  return accounts.map((account) => ({ ...account }));
}

export async function createStaffAccount(
  input: CreateStaffInput,
): Promise<UserAccount> {
  await wait();
  assertUniqueEmail(input.email);

  const today = new Date();
  const dateStr = `${String(today.getDate()).padStart(2, "0")}/${String(
    today.getMonth() + 1,
  ).padStart(2, "0")}/${today.getFullYear()}`;

  const newAccount: UserAccount = {
    id: `user-${crypto.randomUUID().slice(0, 8)}`,
    fullName: input.fullName.trim(),
    email: input.email.trim().toLowerCase(),
    phone: input.phone.trim(),
    role: input.role,
    status: "pending_invitation",
    createdAt: dateStr,
    lastSignInAt: null,
  };

  accounts = [newAccount, ...accounts];
  return { ...newAccount };
}

export async function lockAccount(
  id: string,
  reason: string,
  lockedBy = "Admin OTCMS",
): Promise<UserAccount> {
  await wait(180);
  const current = accounts.find((acc) => acc.id === id);
  if (!current) throw new Error("Không tìm thấy tài khoản.");

  const now = new Date().toISOString();
  const updated: UserAccount = {
    ...current,
    status: "inactive",
    lockReason: reason.trim(),
    lockedBy,
    lockedAt: now,
  };
  accounts = accounts.map((acc) => (acc.id === id ? updated : acc));
  return { ...updated };
}

export async function unlockAccount(id: string): Promise<UserAccount> {
  await wait(150);
  const current = accounts.find((acc) => acc.id === id);
  if (!current) throw new Error("Không tìm thấy tài khoản.");

  const updated: UserAccount = {
    ...current,
    status: "active",
    lockReason: null,
    lockedBy: null,
    lockedAt: null,
  };
  accounts = accounts.map((acc) => (acc.id === id ? updated : acc));
  return { ...updated };
}

export async function updateAccountRole(
  id: string,
  newRole: StaffRole,
): Promise<UserAccount> {
  await wait(200);
  const current = accounts.find((acc) => acc.id === id);
  if (!current) throw new Error("Không tìm thấy tài khoản.");
  if (current.role === "admin" || current.role === "patient") {
    throw new Error("Không thể thay đổi vai trò của tài khoản này qua chức năng quản lý nhân viên.");
  }

  const updated: UserAccount = { ...current, role: newRole };
  accounts = accounts.map((acc) => (acc.id === id ? updated : acc));
  return { ...updated };
}

export async function updateAccountStatus(
  id: string,
  status: AccountStatus,
): Promise<UserAccount> {
  if (status === "inactive") {
    return lockAccount(id, "Tạm khóa bởi quản trị viên");
  }
  return unlockAccount(id);
}

export async function resendStaffInvitation(id: string): Promise<void> {
  await wait(200);
  const current = accounts.find((acc) => acc.id === id);
  if (!current) throw new Error("Không tìm thấy tài khoản.");
  if (current.status !== "pending_invitation") {
    throw new Error("Tài khoản này đã được kích hoạt, không thể gửi lại lời mời.");
  }
}
