import { ApiError } from "@/lib/api/apiError";
import { apiRequest } from "@/lib/api/client";
import {
  loadAccountAvatarUrl,
  notifyAccountAvatarChanged,
} from "@/lib/auth/accountAvatarCache";
import { getSession } from "@/lib/auth/authSession";
import {
  changeFirstLoginPassword,
  FirstLoginPasswordError,
} from "@/lib/auth/changePasswordApi";
import { getCurrentPatientAccount } from "@/lib/auth/currentPatientAccountApi";
import type {
  ChangePasswordInput,
  MyAccount,
  MyAccountAction,
  PasswordChangeResult,
  StaffAccount,
} from "../types/myAccount";
type StaffAccountResponse = {
  id: string;
  kind: "STAFF";
  displayName: string;
  username: string;
  role: StaffAccount["role"];
  status: StaffAccount["status"];
  avatarUrl: string | null;
  mustChangePassword: boolean;
  allowedActions: MyAccountAction[];
};

/** GET /api/v1/me */
export async function getMyAccount(signal?: AbortSignal): Promise<MyAccount> {
  const accountType = (await getSession())?.accountType;
    if (accountType === "PATIENT") {
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
    if (accountType) {
      const current = await apiRequest<StaffAccountResponse>("/api/v1/me", {
        signal,
      });
      const avatarUrl = current.avatarUrl
        ? await loadAccountAvatarUrl(current.id)
        : null;
      return { ...current, avatarUrl };
    }
    throw new ApiError(401, { code: "MSG-14" });
}

/** PUT /api/v1/me/avatar (multipart, part `photo`) */
export async function updateMyAvatar(file: File): Promise<MyAccount> {
  const form = new FormData();
  form.append("photo", file);
  await apiRequest("/api/v1/me/avatar", { method: "PUT", body: form });
  notifyAccountAvatarChanged();
  return getMyAccount();
}

/** DELETE /api/v1/me/avatar */
export async function removeMyAvatar(): Promise<void> {
  await apiRequest("/api/v1/me/avatar", { method: "DELETE" });
  notifyAccountAvatarChanged();
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
  try {
    await changeFirstLoginPassword(input.currentPassword, input.newPassword);
  } catch (error) {
    throw toPasswordApiError(error);
  }
  return { sessionEnded: true };
}
