import { peekSession } from "@/lib/auth/authSession";
import { isApiError } from "@/lib/api/apiError";
import { isMyAccountAccessError } from "./myAccountErrors";
import {
  emitMyAccountAccessLost,
  emitMyAccountChanged,
} from "./myAccountEvents";
import type { MyAccount } from "../types/myAccount";

export class AccountRequestSupersededError extends Error {
  constructor() {
    super("Account request superseded");
    this.name = "AccountRequestSupersededError";
  }
}

/** Không phát dữ liệu/error của một request sang tài khoản khác hoặc flow đã đóng. */
export async function accountMutation<T>(
  operation: () => Promise<T>,
  accountResult?: (result: T) => MyAccount,
  signal?: AbortSignal,
  publish = true,
): Promise<T> {
  const owner = peekSession()?.accountId;
  const isCurrent = () =>
    !signal?.aborted && peekSession()?.accountId === owner;
  try {
    const result = await operation();
    if (!isCurrent()) throw new AccountRequestSupersededError();
    if (publish) emitMyAccountChanged(accountResult?.(result));
    return result;
  } catch (error) {
    if (!isCurrent()) {
      if (!signal?.aborted && owner !== undefined && !peekSession())
        emitMyAccountAccessLost();
      throw new AccountRequestSupersededError();
    }
    // 404 avatar là ảnh không tồn tại; không phải tài khoản mất quyền.
    if (
      isMyAccountAccessError(error) &&
      !(isApiError(error) && error.status === 404)
    )
      emitMyAccountAccessLost();
    throw error;
  }
}
