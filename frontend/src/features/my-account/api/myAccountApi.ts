import { mockDelay, mockFail } from "@/lib/api/mockServer";
import { ApiError } from "@/lib/api/apiError";
import { accountMutation } from "./myAccountMutation";
import { isAvatarFileValid } from "../schema/avatarRules";
import { apiRequest } from "@/lib/api/client";
import { mapMyAccountResponse } from "./myAccountResponse";
import { isPasswordValid } from "@/lib/domain/passwordPolicy";
import type { ChangePasswordInput, MyAccount } from "../types/myAccount";
import {
  MOCK_MY_ACCOUNT,
  MOCK_SERVICE_ERROR_PASSWORD,
} from "./myAccountMockData";
import {
  currentMockAccountState,
  resetMockAccountState,
} from "./myAccountMockStore";

// GET/avatar dùng KAN-29 thật. Adapter mật khẩu chưa thuộc giai đoạn này.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;

/** Chỉ dùng trong test: đặt lại kho dữ liệu giả của loại tài khoản đã cho (mặc định: quản trị). */
export function resetMyAccountMockStore(account: MyAccount = MOCK_MY_ACCOUNT) {
  resetMockAccountState(account);
}

/** GET /api/v1/me: cùng đường HTTP cho mọi vai trò và mọi môi trường. */
export async function getMyAccount(signal?: AbortSignal): Promise<MyAccount> {
  return mapMyAccountResponse(
    await apiRequest<unknown>("/api/v1/me", { signal }),
  );
}
/** PUT /api/v1/me/avatar: browser tự đặt multipart boundary. */
export async function updateMyAvatar(
  file: File,
  signal?: AbortSignal,
): Promise<MyAccount> {
  if (!isAvatarFileValid(file))
    throw new ApiError(400, {
      code: "MSG-08",
      fieldErrors: [{ field: "photo", code: "MSG-08" }],
    });
  const photo = new FormData();
  photo.append("photo", file);
  return accountMutation(
    async () =>
      mapMyAccountResponse(
        await apiRequest<unknown>("/api/v1/me/avatar", {
          method: "PUT",
          body: photo,
          ...(signal ? { signal } : {}),
        }),
      ),
    (account) => account,
    signal,
  );
}

/** DELETE trả 204; refetch /me để giữ projection/quyền authoritative. */
export async function removeMyAvatar(signal?: AbortSignal): Promise<void> {
  return accountMutation(
    () =>
      apiRequest<void>("/api/v1/me/avatar", {
        method: "DELETE",
        ...(signal ? { signal } : {}),
      }),
    undefined,
    signal,
  );
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
