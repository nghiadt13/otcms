import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import {
  loadAccountAvatarUrl,
  notifyAccountAvatarChanged,
} from "@/lib/auth/accountAvatarCache";
import { mockDelay, mockFail } from "@/lib/api/mockServer";
import { getSession } from "@/lib/auth/authSession";
import {
  changeFirstLoginPassword,
  FirstLoginPasswordError,
} from "@/lib/auth/changePasswordApi";
import { getCurrentPatientAccount } from "@/lib/auth/currentPatientAccountApi";
import { isPasswordValid } from "@/lib/domain/passwordPolicy";
import type {
  ChangePasswordInput,
  MyAccount,
  PasswordChangeResult,
} from "../types/myAccount";
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
// apiRequest("/api/v1/me...") (upload ảnh gửi FormData, part `photo`). Tài nguyên của chính người
// đăng nhập nên dùng /api/v1/me, không đặt dưới /admin. Tên hiển thị và email: myAccountProfileApi.ts.
// Bệnh nhân (ngoài môi trường test) đã gọi BE thật; nhân viên/quản trị vẫn dùng kho giả.

const LATENCY_MS = process.env.NODE_ENV === "test" ? 0 : 300;

/** Phiên hiện tại là bệnh nhân và không chạy test: dùng BE thật thay cho kho giả. */
async function isLivePatientSession(): Promise<boolean> {
  if (process.env.NODE_ENV === "test") return false;
  return (await getSession())?.accountType === "PATIENT";
}

/** Chỉ dùng trong test: đặt lại kho dữ liệu giả của loại tài khoản đã cho (mặc định: quản trị). */
export function resetMyAccountMockStore(account: MyAccount = MOCK_MY_ACCOUNT) {
  resetMockAccountState(account);
}

/** GET /api/v1/me */
export async function getMyAccount(signal?: AbortSignal): Promise<MyAccount> {
  if (process.env.NODE_ENV !== "test") {
    if (await isLivePatientSession()) {
      // /me/patient-account không có ảnh; /me cho biết tài khoản đã có ảnh chưa (`avatarUrl`).
      const [current, me] = await Promise.all([
        getCurrentPatientAccount(signal),
        apiRequest<{ avatarUrl: string | null; displayName: string }>(
          "/api/v1/me",
          { signal },
        ),
      ]);
      const avatarUrl = me.avatarUrl
        ? await loadAccountAvatarUrl(current.id)
        : null;
      return {
        id: current.id,
        kind: "PATIENT",
        displayName: current.displayName ?? me.displayName,
        email: current.email,
        status: current.status === "ACTIVE" ? "ACTIVE" : "INACTIVE",
        avatarUrl,
        allowedActions: [
          "updateDisplayName",
          "changePassword",
          "updateAvatar",
          ...(avatarUrl ? (["removeAvatar"] as const) : []),
        ],
      };
    }
  }
  await mockDelay(LATENCY_MS, signal);
  return accountSnapshot(currentMockAccountState().account);
}

/** PUT /api/v1/me/avatar (multipart, part `photo`) */
export async function updateMyAvatar(file: File): Promise<MyAccount> {
  if (await isLivePatientSession()) {
    const form = new FormData();
    form.append("photo", file);
    await apiRequest("/api/v1/me/avatar", { method: "PUT", body: form });
    notifyAccountAvatarChanged();
    return getMyAccount();
  }
  await mockDelay(LATENCY_MS);
  if (
    !AVATAR_ACCEPTED_TYPES.includes(file.type) ||
    file.size > AVATAR_MAX_BYTES
  ) {
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
  if (await isLivePatientSession()) {
    await apiRequest("/api/v1/me/avatar", { method: "DELETE" });
    notifyAccountAvatarChanged();
    return;
  }
  await mockDelay(LATENCY_MS);
  const state = currentMockAccountState();
  state.account = { ...state.account, avatarUrl: null };
}

/**
 * Lỗi của BE đổi mật khẩu → ApiError cùng hình với lớp giả, để form gắn lỗi đúng ô: mật khẩu hiện
 * tại sai → `currentPassword`; mật khẩu mới sai chính sách hoặc trùng mật khẩu cũ → `newPassword`.
 */
export function toPasswordApiError(error: unknown): unknown {
  if (!(error instanceof FirstLoginPasswordError)) return error;
  switch (error.reason) {
    case "current":
      return new ApiError(400, {
        code: "MSG-08",
        fieldErrors: [{ field: "currentPassword", code: "MSG-08" }],
      });
    case "reused":
    case "invalid":
      return new ApiError(400, {
        code: "MSG-08",
        fieldErrors: [{ field: "newPassword", code: "MSG-08" }],
      });
    case "session":
      return new ApiError(401, { code: "MSG-14" });
    default:
      return new ApiError(500, { code: "MSG-04" });
  }
}

/**
 * POST /api/auth/change-password. Bệnh nhân gọi BE thật; BE thu hồi mọi phiên sau khi đổi, nên
 * phiên trong trình duyệt bị xóa và người dùng phải đăng nhập lại.
 */
export async function changeMyPassword(
  input: ChangePasswordInput,
): Promise<PasswordChangeResult> {
  if (await isLivePatientSession()) {
    try {
      await changeFirstLoginPassword(input.currentPassword, input.newPassword);
    } catch (error) {
      throw toPasswordApiError(error);
    }
    return { sessionEnded: true };
  }
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
  return { sessionEnded: false };
}
