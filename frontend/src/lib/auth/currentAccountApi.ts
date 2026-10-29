import { apiRequest } from "@/lib/api/client";
import { getSession, peekSession, type AccountType } from "./authSession";

// Người đang đăng nhập theo backend (GET /api/v1/me): dùng cho tên hiển thị ở header công khai.
// Khác `features/my-account` (kho giả của màn tài khoản): file này gọi backend thật và nằm trong
// lib để mọi khu dùng chung mà không import feature khác.

export type CurrentAccount = {
  id: string;
  accountType: AccountType;
  email: string;
  /** Họ tên hồ sơ bệnh nhân; chưa có hồ sơ thì là phần trước @ của email. */
  displayName: string;
};

// Nhớ theo tài khoản của phiên hiện tại: đổi hoặc mất phiên thì tải lại. `inflight` gộp các
// lời gọi đồng thời (thanh trên và menu di động cùng mở trang).
let cached: { accountId: string; account: CurrentAccount } | null = null;
let inflight: { accountId: string; request: Promise<CurrentAccount> } | null =
  null;

/** Tài khoản của phiên hiện có; `null` khi chưa đăng nhập. Lỗi mạng/BE được ném ra cho nơi gọi. */
export async function fetchCurrentAccount(): Promise<CurrentAccount | null> {
  const session = await getSession();
  if (!session) {
    cached = null;
    return null;
  }
  if (cached?.accountId === session.accountId) return cached.account;
  if (inflight?.accountId === session.accountId) return inflight.request;

  const request = apiRequest<CurrentAccount>("/api/v1/me")
    .then((account) => {
      cached = { accountId: session.accountId, account };
      return account;
    })
    .finally(() => {
      inflight = null;
    });
  inflight = { accountId: session.accountId, request };
  return request;
}

/**
 * Tài khoản đã nhớ của phiên đang giữ, đọc đồng bộ (không gọi mạng, không refresh) để màn vừa
 * mở không nháy khung chờ. `null` khi chưa nhớ hoặc phiên đã đổi/mất.
 */
export function peekCurrentAccount(): CurrentAccount | null {
  const session = peekSession();
  return session && cached?.accountId === session.accountId
    ? cached.account
    : null;
}

/** Bỏ bản nhớ (sau khi đổi tên hiển thị hoặc trong test). */
export function resetCurrentAccountCache(): void {
  cached = null;
  inflight = null;
}
