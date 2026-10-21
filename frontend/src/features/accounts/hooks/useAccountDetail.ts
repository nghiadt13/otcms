"use client";

import { useCallback, useEffect, useState } from "react";
import { isApiError } from "@/lib/api/apiError";
import { messageFromError } from "@/lib/messages/appMessages";
import { getAccount } from "../api/accountApi";
import type { AccountDetail } from "../types/account";

type Result = {
  id: string;
  detail?: AccountDetail;
  error?: unknown;
};

/** Chi tiết một tài khoản cho Sheet. Đổi `id` thì bỏ kết quả của id cũ. */
export function useAccountDetail(id: string) {
  const [result, setResult] = useState<Result | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    getAccount(id).then(
      (detail) => {
        if (!cancelled) setResult({ id, detail });
      },
      (error: unknown) => {
        if (!cancelled) setResult({ id, error });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [id, reloadToken]);

  const current = result?.id === id ? result : null;

  const reload = useCallback(() => {
    setResult(null);
    setReloadToken((n) => n + 1);
  }, []);

  /** Dùng bản ghi mới từ phản hồi đổi trạng thái, khỏi gọi lại GET. */
  const setDetail = useCallback(
    (detail: AccountDetail) => setResult({ id: detail.id, detail }),
    [],
  );

  return {
    detail: current?.detail ?? null,
    loading: current === null,
    error: current?.error ? messageFromError(current.error) : null,
    /** Lỗi gốc, để màn báo toast khi 404. */
    rawError: current?.error ?? null,
    /** 404: tài khoản không còn, màn đóng Sheet và tải lại danh sách. */
    notFound: isApiError(current?.error) && current.error.status === 404,
    reload,
    setDetail,
  };
}
