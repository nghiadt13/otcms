import { mockDelay, mockFail } from "@/lib/api/mockServer";
import type { MyAccount } from "../types/myAccount";
import { MOCK_EMAIL_CHANGE_OTP, MOCK_TAKEN_EMAIL } from "./myAccountMockData";
import { accountSnapshot, currentMockAccountState } from "./myAccountMockStore";

// Thông tin đăng nhập của bệnh nhân: tên hiển thị và email (đổi bằng OTP gửi tới email mới).
// Cùng tài nguyên `/api/v1/me`. LỚP API GIẢ như myAccountApi.ts: khi có backend giữ nguyên
// chữ ký hàm. Thông tin hành chính (ngày sinh, SĐT, địa chỉ) không ở đây (UC14, hồ sơ).

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;
const DISPLAY_NAME_MIN = 2;
const DISPLAY_NAME_MAX = 100;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

/** PATCH /api/v1/me, body `{ displayName }`. Sai độ dài: 400 MSG-12 gắn vào `displayName`. */
export async function updateMyDisplayName(
  displayName: string,
): Promise<MyAccount> {
  await mockDelay(LATENCY_MS);
  const state = currentMockAccountState();
  if (state.account.kind !== "PATIENT") mockFail(403, "MSG-14");
  const name = displayName.trim();
  if (name.length < DISPLAY_NAME_MIN || name.length > DISPLAY_NAME_MAX) {
    mockFail(400, "MSG-12", undefined, [
      { field: "displayName", code: "MSG-12" },
    ]);
  }
  state.account = { ...state.account, displayName: name };
  return accountSnapshot(state.account);
}

/**
 * POST /api/v1/me/email-change, body `{ newEmail }`. 204: gửi OTP tới email mới.
 * Email cũ vẫn hiệu lực tới khi xác minh xong (BR-05). Email đã thuộc tài khoản khác: 400 MSG-08.
 */
export async function requestMyEmailChange(newEmail: string): Promise<void> {
  await mockDelay(LATENCY_MS);
  const state = currentMockAccountState();
  const account = state.account;
  if (account.kind !== "PATIENT") mockFail(403, "MSG-14");
  const email = newEmail.trim();
  if (!EMAIL_PATTERN.test(email)) {
    mockFail(400, "MSG-12", undefined, [{ field: "newEmail", code: "MSG-12" }]);
  }
  // Trùng email đang dùng hoặc đã thuộc tài khoản khác: cùng một thông báo chung (BR-03).
  if (email === MOCK_TAKEN_EMAIL || email === account.email) {
    mockFail(400, "MSG-08", undefined, [{ field: "newEmail", code: "MSG-08" }]);
  }
  state.pendingEmail = email;
}

/**
 * POST /api/v1/me/email-change/verify, body `{ otp }`. Đúng mã thì email mới thành email
 * của tài khoản. Không có yêu cầu đang chờ hoặc mã sai: 400 MSG-08.
 */
export async function verifyMyEmailChange(otp: string): Promise<MyAccount> {
  await mockDelay(LATENCY_MS);
  const state = currentMockAccountState();
  const account = state.account;
  if (account.kind !== "PATIENT") mockFail(403, "MSG-14");
  const pending = state.pendingEmail;
  if (pending === null) mockFail(400, "MSG-08");
  if (otp !== MOCK_EMAIL_CHANGE_OTP) {
    mockFail(400, "MSG-08", undefined, [{ field: "otp", code: "MSG-08" }]);
  }
  state.account = { ...account, email: pending };
  state.pendingEmail = null;
  return accountSnapshot(state.account);
}
