import { mockDelay, mockFail } from "@/lib/api/mockServer";
import { updateCurrentPatientDisplayName } from "@/lib/auth/currentPatientAccountApi";
import { getMyAccount } from "./myAccountApi";
import type { MyAccount } from "../types/myAccount";
import { accountSnapshot, currentMockAccountState } from "./myAccountMockStore";

// Thông tin đăng nhập của bệnh nhân: tên hiển thị. Email đăng nhập chỉ xem, không đổi trên web.
// Cùng tài nguyên `/api/v1/me`. LỚP API GIẢ như myAccountApi.ts: khi có backend giữ nguyên
// chữ ký hàm. Thông tin hành chính (ngày sinh, SĐT, địa chỉ) không ở đây (UC14, hồ sơ).

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;
const DISPLAY_NAME_MIN = 2;
const DISPLAY_NAME_MAX = 100;

/** PATCH /api/v1/me, body `{ displayName }`. Sai độ dài: 400 MSG-12 gắn vào `displayName`. */
export async function updateMyDisplayName(
  displayName: string,
): Promise<MyAccount> {
  if (process.env.NODE_ENV !== "test") {
    await updateCurrentPatientDisplayName(displayName);
    return getMyAccount();
  }
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
