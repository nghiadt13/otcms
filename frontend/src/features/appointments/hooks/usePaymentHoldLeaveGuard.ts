"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  clearCancelKeepalive,
  primeCancelKeepalive,
  sendCancelKeepalive,
} from "../api/pendingHoldLiveApi";

// Canh việc rời bước thanh toán khi còn giữ chỗ (rời trang = hủy thanh toán). App Router không có API
// chặn đổi route, nên canh ở lớp bọc:
//  - Bấm liên kết (<a>/<Link>): chặn click ở pha capture, hỏi xác nhận, đồng ý thì hủy rồi bấm lại
//    chính liên kết đó (đi qua Link của Next như bình thường).
//  - Back: đẩy một mục "đệm" vào lịch sử; Back bật mục đệm ra thì hỏi xác nhận. Đồng ý thì hủy rồi
//    lùi thêm một bước; không đồng ý thì đẩy lại mục đệm. Hệ quả: sau khi bước thanh toán kết thúc
//    mà không lùi mục đệm, một lần Back đầu tiên chỉ ở nguyên trang.
//  - Đóng tab / điều hướng ra ngoài: `pagehide` gửi yêu cầu hủy bằng fetch keepalive (nỗ lực tối đa).
//  - Component bị gỡ khi còn giữ chỗ (đổi route bằng code, ví dụ router.push): gọi `onAbandoned`.
// Không dùng `beforeunload` để hủy vì không tin cậy.

/** Cách đi tiếp sau khi người dùng đồng ý hủy. */
export type LeavePrompt =
  | { kind: "link"; anchor: HTMLAnchorElement }
  | { kind: "back" };

const GUARD_STATE_KEY = "__otcmsPaymentHoldGuard";
/** Làm mới token cho `pagehide`: giữ access token còn hạn trong suốt 15 phút giữ chỗ. */
const PRIME_INTERVAL_MS = 60_000;
/** Thời gian chờ popstate của lần lùi do `completeLeave` gây ra trước khi gỡ cờ bỏ qua. */
const BACK_BYPASS_MS = 500;

function isPlainLeftClick(event: MouseEvent): boolean {
  return (
    event.button === 0 &&
    !event.metaKey &&
    !event.ctrlKey &&
    !event.shiftKey &&
    !event.altKey
  );
}

/** Liên kết rời trang hiện tại (cùng trang hoặc chỉ khác #hash thì không tính). */
function leavingAnchor(event: MouseEvent): HTMLAnchorElement | null {
  const target = event.target;
  if (!(target instanceof Element)) return null;
  const anchor = target.closest("a[href]");
  if (!(anchor instanceof HTMLAnchorElement)) return null;
  if (anchor.target === "_blank" || anchor.hasAttribute("download")) return null;
  let url: URL;
  try {
    url = new URL(anchor.href, window.location.href);
  } catch {
    return null;
  }
  if (!/^https?:$/.test(url.protocol)) return null;
  const here = window.location;
  return url.origin === here.origin &&
    url.pathname === here.pathname &&
    url.search === here.search
    ? null
    : anchor;
}

export function usePaymentHoldLeaveGuard({
  active,
  onAbandoned,
}: {
  /** Còn giữ chỗ. false thì gỡ mọi canh. */
  active: boolean;
  /** Component bị gỡ khi còn giữ chỗ. Gọi hoãn một nhịp để không nhầm với chạy lại effect của StrictMode. */
  onAbandoned: () => void;
}) {
  const [prompt, setPrompt] = useState<LeavePrompt | null>(null);
  const bypassRef = useRef(false);
  const promptRef = useRef<LeavePrompt | null>(null);
  const activeRef = useRef(active);
  const onAbandonedRef = useRef(onAbandoned);
  const abandonTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Giữ giá trị mới nhất cho các listener và cleanup (không ghi ref lúc render).
  useEffect(() => {
    activeRef.current = active;
    onAbandonedRef.current = onAbandoned;
    promptRef.current = prompt;
  });

  useEffect(() => {
    if (!active) return;

    const onClick = (event: MouseEvent) => {
      if (bypassRef.current || event.defaultPrevented) return;
      if (!isPlainLeftClick(event)) return;
      const anchor = leavingAnchor(event);
      if (!anchor) return;
      event.preventDefault();
      event.stopPropagation();
      setPrompt({ kind: "link", anchor });
    };
    const onPopState = () => {
      // Lùi do chính `completeLeave` gây ra (giữ chỗ đã hủy): không hỏi lại.
      if (bypassRef.current) {
        bypassRef.current = false;
        return;
      }
      setPrompt({ kind: "back" });
    };
    const onPageHide = () => {
      sendCancelKeepalive();
    };

    if (!window.history.state?.[GUARD_STATE_KEY])
      window.history.pushState(
        { [GUARD_STATE_KEY]: true },
        "",
        window.location.href,
      );
    void primeCancelKeepalive();
    const primeTimer = window.setInterval(
      () => void primeCancelKeepalive(),
      PRIME_INTERVAL_MS,
    );
    window.addEventListener("click", onClick, true);
    window.addEventListener("popstate", onPopState);
    window.addEventListener("pagehide", onPageHide);
    return () => {
      window.clearInterval(primeTimer);
      window.removeEventListener("click", onClick, true);
      window.removeEventListener("popstate", onPopState);
      window.removeEventListener("pagehide", onPageHide);
      clearCancelKeepalive();
    };
  }, [active]);

  // Gỡ component khi còn giữ chỗ. StrictMode (dev) chạy cleanup rồi setup lại ngay: setup hủy hẹn.
  useEffect(() => {
    if (abandonTimer.current !== null) {
      clearTimeout(abandonTimer.current);
      abandonTimer.current = null;
    }
    return () => {
      if (!activeRef.current) return;
      abandonTimer.current = setTimeout(() => {
        abandonTimer.current = null;
        onAbandonedRef.current();
      }, 0);
    };
  }, []);

  /** "Tiếp tục thanh toán": ở lại. Back đã bật mục đệm ra nên đẩy lại để canh tiếp. */
  const dismiss = useCallback(() => {
    if (promptRef.current?.kind === "back")
      window.history.pushState(
        { [GUARD_STATE_KEY]: true },
        "",
        window.location.href,
      );
    setPrompt(null);
  }, []);

  /** Giữ chỗ đã hủy xong: đi tiếp đúng nơi người dùng định tới. */
  const completeLeave = useCallback(() => {
    const current = promptRef.current;
    setPrompt(null);
    if (!current) return;
    if (current.kind === "back") {
      // popstate do chính lần lùi này gây ra được onPopState bỏ qua và gỡ cờ. Không lùi được (đầu
      // lịch sử, tab mới) thì không có popstate: hẹn gỡ cờ để không bỏ qua nhầm lần Back sau.
      bypassRef.current = true;
      window.history.back();
      setTimeout(() => {
        bypassRef.current = false;
      }, BACK_BYPASS_MS);
      return;
    }
    bypassRef.current = true;
    try {
      current.anchor.click();
    } finally {
      bypassRef.current = false;
    }
  }, []);

  /** Đóng hộp thoại mà không đi tiếp cũng không đẩy lại mục đệm (giữ chỗ đã kết thúc). */
  const closePrompt = useCallback(() => setPrompt(null), []);

  return { prompt, dismiss, completeLeave, closePrompt };
}
