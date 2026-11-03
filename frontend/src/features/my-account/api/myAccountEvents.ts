import type { MyAccount } from "../types/myAccount";

export const MY_ACCOUNT_CHANGED = "my-account:changed";
export const MY_ACCOUNT_ACCESS_LOST = "my-account:access-lost";

/** Không truyền account: giữ hành vi refetch của các luồng chưa tích hợp. */
export function emitMyAccountChanged(account?: MyAccount): void {
  window.dispatchEvent(
    new CustomEvent(MY_ACCOUNT_CHANGED, { detail: account }),
  );
}

export function emitMyAccountAccessLost(): void {
  window.dispatchEvent(new Event(MY_ACCOUNT_ACCESS_LOST));
}
