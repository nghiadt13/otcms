import { getMockSignedInKind } from "@/lib/session/mockSignedInKind";
import type { MyAccount } from "../types/myAccount";
import {
  MOCK_CORRECT_CURRENT_PASSWORD,
  MOCK_MY_ACCOUNT,
  MOCK_PATIENT_ACCOUNT,
} from "./myAccountMockData";

// KHO GIẢ của `/api/v1/me` trong bộ nhớ. Mỗi loại tài khoản giữ trạng thái riêng (nhân viên
// và bệnh nhân không dùng chung dữ liệu); loại đang "đăng nhập" lấy từ phiên giả
// (lib/session/mockSignedInKind). Xóa file này khi có backend.

export interface MockAccountState {
  account: MyAccount;
  password: string;
  /** Email mới đang chờ mã OTP; email cũ vẫn hiệu lực tới khi xác minh xong (BR-05). */
  pendingEmail: string | null;
}

function initialState(account: MyAccount): MockAccountState {
  return {
    account: { ...account },
    password: MOCK_CORRECT_CURRENT_PASSWORD,
    pendingEmail: null,
  };
}

const states: Record<MyAccount["kind"], MockAccountState> = {
  STAFF: initialState(MOCK_MY_ACCOUNT),
  PATIENT: initialState(MOCK_PATIENT_ACCOUNT),
};

/** Trạng thái của tài khoản đang đăng nhập giả; nơi gọi đọc và ghi trực tiếp. */
export function currentMockAccountState(): MockAccountState {
  return states[getMockSignedInKind()];
}

/** Chỉ dùng trong test: đặt lại kho của loại tài khoản đã cho. */
export function resetMockAccountState(account: MyAccount): void {
  states[account.kind] = initialState(account);
}

/** Bản sao gửi ra ngoài, kèm `allowedActions` theo trạng thái hiện tại. */
export function accountSnapshot(account: MyAccount): MyAccount {
  const allowedActions: MyAccount["allowedActions"] = [
    "updateAvatar",
    "changePassword",
  ];
  if (account.kind === "PATIENT")
    allowedActions.push("updateDisplayName", "changeEmail");
  // `removeAvatar` chỉ có khi đang có ảnh.
  if (account.avatarUrl) allowedActions.splice(1, 0, "removeAvatar");
  return { ...account, allowedActions };
}
