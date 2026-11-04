import { accountMutation } from "./myAccountMutation";
import { apiRequest } from "@/lib/api/client";
import { ApiError } from "@/lib/api/apiError";
import { peekSession } from "@/lib/auth/authSession";
import { mapMyAccountResponse } from "./myAccountResponse";
import { isMyAccountAccessError } from "./myAccountErrors";
import {
  emitMyAccountChanged,
  emitMyAccountAccessLost,
} from "./myAccountEvents";
import {
  DISPLAY_NAME_MIN,
  DISPLAY_NAME_MAX,
  displayNameLength,
  trimDisplayName,
} from "../schema/displayNameRules";
import type { MyAccount } from "../types/myAccount";

/** PATCH KAN-29: {displayName}; quyền và forced-password do backend kiểm tra. */
export async function updateMyDisplayName(
  displayName: string,
): Promise<MyAccount> {
  const name = trimDisplayName(displayName);
  const length = displayNameLength(name);
  if (length < DISPLAY_NAME_MIN || length > DISPLAY_NAME_MAX)
    throw new ApiError(400, {
      code: "MSG-08",
      fieldErrors: [{ field: "displayName", code: "MSG-08" }],
    });
  const accountIdAtStart = peekSession()?.accountId;
  const belongsToAnotherAccount = () => {
    const currentId = peekSession()?.accountId;
    return (
      accountIdAtStart !== undefined &&
      currentId !== undefined &&
      currentId !== accountIdAtStart
    );
  };
  try {
    const updated = mapMyAccountResponse(
      await apiRequest<unknown>("/api/v1/me", {
        method: "PATCH",
        body: JSON.stringify({ displayName: name }),
      }),
    );
    if (!belongsToAnotherAccount()) emitMyAccountChanged(updated);
    return updated;
  } catch (error) {
    if (!belongsToAnotherAccount() && isMyAccountAccessError(error))
      emitMyAccountAccessLost();
    throw error;
  }
}
/** 202 là xác nhận tiếp nhận chung, kể cả khi backend suppress yêu cầu. */
export async function requestMyEmailChange(
  newEmail: string,
  signal?: AbortSignal,
): Promise<void> {
  // Không publish account/event trong bước pending OTP.
  await accountMutation(
    () =>
      apiRequest<unknown>("/api/v1/me/email-change", {
        method: "POST",
        body: JSON.stringify({ newEmail: newEmail.trim() }),
        ...(signal ? { signal } : {}),
      }),
    undefined,
    signal,
    false,
  );
}

/** Chỉ projection server sau verify thành công mới thay email chính thức. */
export async function verifyMyEmailChange(
  code: string,
  signal?: AbortSignal,
): Promise<MyAccount> {
  return accountMutation(
    async () =>
      mapMyAccountResponse(
        await apiRequest<unknown>("/api/v1/me/email-change/verify", {
          method: "POST",
          body: JSON.stringify({ code }),
          ...(signal ? { signal } : {}),
        }),
      ),
    (account) => account,
    signal,
  );
}
