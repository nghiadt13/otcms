"use client";

import { useEffect, useState } from "react";
import { loadAccountAvatarUrl } from "@/lib/auth/accountAvatarCache";

/**
 * Object URL ảnh đại diện của tài khoản đang đăng nhập, để header/menu hiện cùng ảnh với màn tài
 * khoản. `null` khi chưa đăng nhập, tài khoản chưa có ảnh (`avatarUrl` của /me rỗng) hoặc tải hỏng.
 * Đổi ảnh: `useCurrentAccount` đọc lại /me nên `avatarUrl` đổi và hook tải lại ảnh mới.
 */
export function useAccountAvatarUrl(
  account: { id: string; avatarUrl?: string | null } | null,
): string | null {
  const accountId = account?.id ?? null;
  const avatarKey = account?.avatarUrl ?? null;
  const [loaded, setLoaded] = useState<{ key: string; url: string | null }>();

  useEffect(() => {
    if (!accountId || !avatarKey) return;
    let active = true;
    loadAccountAvatarUrl(accountId).then((url) => {
      if (active) setLoaded({ key: `${accountId}|${avatarKey}`, url });
    });
    return () => {
      active = false;
    };
  }, [accountId, avatarKey]);

  if (!accountId || !avatarKey) return null;
  return loaded?.key === `${accountId}|${avatarKey}` ? loaded.url : null;
}
