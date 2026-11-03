"use client";

import { useEffect, useState } from "react";
import { peekSession } from "@/lib/auth/authSession";
import { isApiError } from "@/lib/api/apiError";
import { fetchBlob } from "@/lib/api/client";
import { emitMyAccountAccessLost } from "../api/myAccountEvents";
import {
  isMyAccountAccessError,
  myAccountErrorMessage,
} from "../api/myAccountErrors";
import type { MyAccount } from "../types/myAccount";

/** URL protected chỉ đi vào fetchBlob; consumer chỉ nhận object URL hoặc null. */
export function useAccountAvatar(account: MyAccount | null) {
  const [image, setImage] = useState<{ source: MyAccount; url: string } | null>(
    null,
  );
  const [failure, setFailure] = useState<{
    source: MyAccount;
    message: string;
  } | null>(null);
  useEffect(() => {
    if (!account?.avatarUrl || account.mustChangePassword) return;
    const controller = new AbortController();
    let objectUrl: string | undefined;
    const current = () =>
      !controller.signal.aborted && peekSession()?.accountId === account.id;
    // Chỉ gọi endpoint đã xác minh; không fetch URL tùy ý từ response.
    fetchBlob(`/api/v1/accounts/${encodeURIComponent(account.id)}/avatar`, {
      signal: controller.signal,
    }).then(
      ({ blob }) => {
        if (!current()) {
          if (!controller.signal.aborted && !peekSession())
            emitMyAccountAccessLost();
          return;
        }
        objectUrl = URL.createObjectURL(blob);
        setImage({ source: account, url: objectUrl });
      },
      (error: unknown) => {
        if (!current()) {
          if (!controller.signal.aborted && !peekSession())
            emitMyAccountAccessLost();
          return;
        }
        if (
          isMyAccountAccessError(error) &&
          !(isApiError(error) && error.status === 404)
        ) {
          emitMyAccountAccessLost();
          return;
        }
        setFailure({
          source: account,
          message:
            isApiError(error) && error.status === 404
              ? "Không tải được ảnh đại diện."
              : myAccountErrorMessage(error),
        });
      },
    );
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [account]);
  return {
    avatarUrl: image?.source === account ? image.url : null,
    avatarError: failure?.source === account ? failure.message : null,
  };
}
