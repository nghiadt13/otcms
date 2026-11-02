"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { isApiError, toFieldErrorMap } from "@/lib/api/apiError";
import { formatAppMessage } from "@/lib/messages/appMessages";
import { notifyError, notifySuccess } from "@/lib/messages/notify";
import {
  approveModerationReview,
  getModerationReview,
  hideModerationReview,
  replyModerationReview,
  unhideModerationReview,
} from "../api/reviewModerationApi";
import { validateReviewReply } from "../schema/reviewReplyFieldRules";
import type { ReviewDetail } from "../types/reviewModeration";

export type ReviewDecision = "approve" | "hide" | "unhide";
export type ReplyResult = { ok: boolean; fieldError?: string };

const DECISIONS = {
  approve: approveModerationReview,
  hide: hideModerationReview,
  unhide: unhideModerationReview,
};

// Spec MSG-58/59 khác câu trong bảng nháp APP_MESSAGES → dùng đúng chữ spec.
const DECISION_SUCCESS = "Đã cập nhật trạng thái hiển thị của đánh giá.";
const REPLY_SUCCESS = "Đã lưu phản hồi của phòng khám.";

type Handlers = {
  /** Dữ liệu đã đổi: màn danh sách tải lại (số đếm tab, trạng thái dòng). */
  onChanged: () => void;
  /** Đánh giá không còn / mất quyền: đóng Sheet. */
  onGone: () => void;
};

type Loaded = { id: string; detail?: ReviewDetail; failed?: boolean };

/** Chi tiết một đánh giá trong Sheet + các thao tác quyết định/phản hồi. */
export function useReviewModerationDetail(
  reviewId: string | null,
  handlers: Handlers,
) {
  const [result, setResult] = useState<Loaded | null>(null);
  const [token, setToken] = useState(0);
  const [busy, setBusy] = useState(false);
  const handlersRef = useRef(handlers);
  useEffect(() => {
    handlersRef.current = handlers;
  });

  useEffect(() => {
    if (!reviewId) return;
    let ignore = false; // bỏ kết quả cũ khi đổi id hoặc đóng Sheet
    getModerationReview(reviewId)
      .then((detail) => {
        if (!ignore) setResult({ id: reviewId, detail });
      })
      .catch((err: unknown) => {
        if (ignore) return;
        if (isApiError(err) && (err.status === 403 || err.status === 404)) {
          notifyError(err);
          handlersRef.current.onGone();
          return;
        }
        setResult({ id: reviewId, failed: true });
      });
    return () => {
      ignore = true;
    };
  }, [reviewId, token]);

  const current = reviewId && result?.id === reviewId ? result : null;

  const retry = useCallback(() => {
    setResult(null);
    setToken((n) => n + 1);
  }, []);

  /** 409 → báo + GET lại (không đoán trạng thái); 403/404 → báo + đóng Sheet. */
  const handleActionError = useCallback((err: unknown) => {
    notifyError(err);
    if (!isApiError(err)) return;
    if (err.status === 409) {
      setToken((n) => n + 1);
      handlersRef.current.onChanged();
    } else if (err.status === 403 || err.status === 404) {
      handlersRef.current.onGone();
    }
  }, []);

  const decide = useCallback(
    async (decision: ReviewDecision): Promise<boolean> => {
      if (!reviewId) return false;
      setBusy(true);
      try {
        const detail = await DECISIONS[decision](reviewId);
        setResult({ id: reviewId, detail });
        notifySuccess(DECISION_SUCCESS);
        handlersRef.current.onChanged();
        return true;
      } catch (err) {
        handleActionError(err);
        return false;
      } finally {
        setBusy(false);
      }
    },
    [reviewId, handleActionError],
  );

  const saveReply = useCallback(
    async (text: string): Promise<ReplyResult> => {
      if (!reviewId) return { ok: false };
      const invalid = validateReviewReply(text);
      if (invalid) return { ok: false, fieldError: formatAppMessage(invalid) };
      setBusy(true);
      try {
        const detail = await replyModerationReview(reviewId, {
          text: text.trim(),
        });
        setResult({ id: reviewId, detail });
        notifySuccess(REPLY_SUCCESS);
        handlersRef.current.onChanged();
        return { ok: true };
      } catch (err) {
        // MSG-57: đỏ dưới ô, không toast.
        const code = isApiError(err)
          ? toFieldErrorMap(err.fieldErrors).text
          : undefined;
        if (code) return { ok: false, fieldError: formatAppMessage(code) };
        handleActionError(err);
        return { ok: false };
      } finally {
        setBusy(false);
      }
    },
    [reviewId, handleActionError],
  );

  return {
    detail: current?.detail ?? null,
    loading: reviewId !== null && current === null,
    failed: current?.failed === true,
    busy,
    retry,
    decide,
    saveReply,
  };
}
