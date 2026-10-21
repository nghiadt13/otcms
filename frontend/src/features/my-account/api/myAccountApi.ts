import { mockDelay, mockFail } from "@/lib/api/mockServer";
import { isPasswordValid } from "@/lib/domain/passwordPolicy";
import type { ChangePasswordInput, MyAccount } from "../types/myAccount";
import {
  AVATAR_ACCEPTED_TYPES,
  AVATAR_MAX_BYTES,
  MOCK_CORRECT_CURRENT_PASSWORD,
  MOCK_MY_ACCOUNT,
  MOCK_SERVICE_ERROR_PASSWORD,
} from "./myAccountMockData";

// LỚP API GIẢ: dữ liệu nằm trong bộ nhớ. Khi có backend giữ nguyên chữ ký hàm,
// đổi thân sang apiRequest("/api/v1/me...") (upload ảnh gửi FormData, part `file`).
// Tài nguyên của chính người đăng nhập nên dùng /api/v1/me, không đặt dưới /admin.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;

let store: MyAccount = { ...MOCK_MY_ACCOUNT };
let currentPassword = MOCK_CORRECT_CURRENT_PASSWORD;

/** Chỉ dùng trong test: đặt lại kho dữ liệu giả. */
export function resetMyAccountMockStore(account: MyAccount = MOCK_MY_ACCOUNT) {
  store = { ...account };
  currentPassword = MOCK_CORRECT_CURRENT_PASSWORD;
}

function withActions(account: MyAccount): MyAccount {
  const allowedActions: MyAccount["allowedActions"] = [
    "updateAvatar",
    "changePassword",
  ];
  // `removeAvatar` chỉ có khi đang có ảnh.
  if (account.avatarUrl) allowedActions.splice(1, 0, "removeAvatar");
  return { ...account, allowedActions };
}

/** GET /api/v1/me */
export async function getMyAccount(signal?: AbortSignal): Promise<MyAccount> {
  await mockDelay(LATENCY_MS, signal);
  return withActions(store);
}

/** PUT /api/v1/me/avatar (multipart, part `file`) */
export async function updateMyAvatar(file: File): Promise<MyAccount> {
  await mockDelay(LATENCY_MS);
  if (
    !AVATAR_ACCEPTED_TYPES.includes(file.type) ||
    file.size > AVATAR_MAX_BYTES
  ) {
    mockFail(400, "MSG-08", undefined, [{ field: "file", code: "MSG-08" }]);
  }
  store = {
    ...store,
    avatarUrl:
      typeof URL.createObjectURL === "function"
        ? URL.createObjectURL(file)
        : "https://files.example.com/avatars/mock.jpg",
  };
  return withActions(store);
}

/** DELETE /api/v1/me/avatar */
export async function removeMyAvatar(): Promise<void> {
  await mockDelay(LATENCY_MS);
  store = { ...store, avatarUrl: null };
}

/** POST /api/v1/me/password */
export async function changeMyPassword(
  input: ChangePasswordInput,
): Promise<void> {
  await mockDelay(LATENCY_MS);
  if (input.currentPassword === MOCK_SERVICE_ERROR_PASSWORD) {
    mockFail(500, "MSG-04");
  }
  if (input.currentPassword !== currentPassword) {
    mockFail(400, "MSG-08", undefined, [
      { field: "currentPassword", code: "MSG-08" },
    ]);
  }
  if (!isPasswordValid(input.newPassword)) {
    mockFail(400, "MSG-08", undefined, [
      { field: "newPassword", code: "MSG-08" },
    ]);
  }
  currentPassword = input.newPassword;
  store = { ...store, mustChangePassword: false };
}
