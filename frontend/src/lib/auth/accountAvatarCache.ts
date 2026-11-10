import { fetchBlob } from "@/lib/api/client";

// Ảnh đại diện của tài khoản đang đăng nhập, dùng chung cho mọi nơi hiện ảnh (header trang chủ,
// header khu bệnh nhân, cột menu khu cá nhân, màn tài khoản). Ảnh nằm sau xác thực Bearer nên thẻ
// <img> không tự tải được: tải về thành blob rồi dùng object URL, nhớ theo tài khoản để không tạo
// thêm URL mỗi lần tải lại. Đổi/gỡ ảnh thì gọi `notifyAccountAvatarChanged` để mọi nơi tải lại.

export const ACCOUNT_AVATAR_CHANGED_EVENT = "account-avatar:changed";

const objectUrls = new Map<string, string>();
const inflight = new Map<string, Promise<string | null>>();

/** Object URL ảnh của tài khoản; `null` khi ảnh hỏng hoặc đã bị gỡ (nơi gọi hiện chữ cái đầu). */
export async function loadAccountAvatarUrl(
  accountId: string,
): Promise<string | null> {
  const cached = objectUrls.get(accountId);
  if (cached) return cached;
  const pending = inflight.get(accountId);
  if (pending) return pending;
  const request = fetchBlob(`/api/v1/accounts/${accountId}/avatar`)
    .then(({ blob }) => {
      const url = URL.createObjectURL(blob);
      objectUrls.set(accountId, url);
      return url;
    })
    .catch(() => null)
    .finally(() => inflight.delete(accountId));
  inflight.set(accountId, request);
  return request;
}

/** Xóa ảnh đã nhớ (sau khi đổi/gỡ ảnh); không báo cho ai. */
export function forgetAccountAvatarUrls(): void {
  for (const url of objectUrls.values()) URL.revokeObjectURL(url);
  objectUrls.clear();
  inflight.clear();
}

/** Sau khi đổi/gỡ ảnh: xóa ảnh đã nhớ rồi báo mọi nơi đang hiện ảnh tải lại. */
export function notifyAccountAvatarChanged(): void {
  forgetAccountAvatarUrls();
  if (typeof window !== "undefined") {
    window.dispatchEvent(new Event(ACCOUNT_AVATAR_CHANGED_EVENT));
  }
}
