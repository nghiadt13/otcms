import { mockDelay, mockFail } from "@/lib/api/mockServer";
import { isPasswordValid } from "@/lib/domain/passwordPolicy";
import type { ChangePasswordInput, MyAccount } from "../types/myAccount";
import {
  AVATAR_ACCEPTED_TYPES,
  AVATAR_MAX_BYTES,
  MOCK_MY_ACCOUNT,
  MOCK_SERVICE_ERROR_PASSWORD,
} from "./myAccountMockData";
import {
  accountSnapshot,
  currentMockAccountState,
  resetMockAccountState,
} from "./myAccountMockStore";

// LỚP API GIẢ: dữ liệu nằm trong bộ nhớ. Khi có backend giữ nguyên chữ ký hàm, đổi thân sang
// apiRequest("/api/v1/me...") (upload ảnh gửi FormData, part `file`). Tài nguyên của chính người
// đăng nhập nên dùng /api/v1/me, không đặt dưới /admin. Tên hiển thị và email: myAccountProfileApi.ts.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;

/** Chỉ dùng trong test: đặt lại kho dữ liệu giả của loại tài khoản đã cho (mặc định: quản trị). */
export function resetMyAccountMockStore(account: MyAccount = MOCK_MY_ACCOUNT) {
  resetMockAccountState(account);
}

/** GET /api/v1/me */
export async function getMyAccount(signal?: AbortSignal): Promise<MyAccount> {
  await mockDelay(LATENCY_MS, signal);
  return accountSnapshot(currentMockAccountState().account);
}

/** PUT /api/v1/me/avatar (multipart, part `file`) */
export async function updateMyAvatar(file: File): Promise<MyAccount> {
  await mockDelay(LATENCY_MS);
  if (!AVATAR_ACCEPTED_TYPES.includes(file.type) || file.size > AVATAR_MAX_BYTES) {
    mockFail(400, "MSG-08", undefined, [{ field: "file", code: "MSG-08" }]);
  }
  const state = currentMockAccountState();
  state.account = {
    ...state.account,
    avatarUrl:
      typeof URL.createObjectURL === "function"
        ? URL.createObjectURL(file)
        : "https://files.example.com/avatars/mock.jpg",
  };
  return accountSnapshot(state.account);
}

/** DELETE /api/v1/me/avatar */
export async function removeMyAvatar(): Promise<void> {
  await mockDelay(LATENCY_MS);
  const state = currentMockAccountState();
  state.account = { ...state.account, avatarUrl: null };
}

/** POST /api/v1/me/password */
export async function changeMyPassword(
  input: ChangePasswordInput,
): Promise<void> {
  await mockDelay(LATENCY_MS);
  if (input.currentPassword === MOCK_SERVICE_ERROR_PASSWORD) {
    mockFail(500, "MSG-04");
  }
  const state = currentMockAccountState();
  if (input.currentPassword !== state.password) {
    mockFail(400, "MSG-08", undefined, [
      { field: "currentPassword", code: "MSG-08" },
    ]);
  }
  if (!isPasswordValid(input.newPassword)) {
    mockFail(400, "MSG-08", undefined, [
      { field: "newPassword", code: "MSG-08" },
    ]);
  }
  state.password = input.newPassword;
  // Đổi xong thì hết trạng thái "mật khẩu khởi tạo" (chỉ tài khoản nhân viên có).
  if (state.account.kind === "STAFF") {
    state.account = { ...state.account, mustChangePassword: false };
  }
}
