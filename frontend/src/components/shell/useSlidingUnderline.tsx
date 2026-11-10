"use client";

import { usePathname } from "next/navigation";
import {
  type FocusEvent,
  type MouseEvent,
  useCallback,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
} from "react";

type Span = { left: number; width: number };

// useLayoutEffect cảnh báo khi render trên máy chủ; trên server dùng useEffect (không chạy).
const useIsoLayoutEffect =
  typeof window === "undefined" ? useEffect : useLayoutEffect;

/**
 * Gạch dưới trượt cho thanh menu ngang. Gạch nằm ở mục đang mở (`aria-current="page"`); rê chuột
 * hoặc focus bàn phím vào một link thì gạch trượt theo tới link đó, rời đi thì trượt về mục đang
 * mở. Dùng chung cho menu trang chủ và menu khu bệnh nhân.
 *
 * Cách dùng: `<ul {...navProps}>` (phần tử cha chứa các `<a>` của menu, cần `relative`) và vẽ
 * `{indicator}` bên trong nó. Gạch rộng bằng chữ: chiều rộng link trừ đệm ngang của link.
 */
export function useSlidingUnderline<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const pathname = usePathname();
  const [span, setSpan] = useState<Span | null>(null);

  // `target` rỗng thì về mục đang mở; không có mục đang mở thì ẩn gạch.
  const place = useCallback((target?: Element | null) => {
    const root = ref.current;
    if (!root) return;
    const link = target ?? root.querySelector('a[aria-current="page"]');
    if (!link) {
      setSpan(null);
      return;
    }
    const style = getComputedStyle(link);
    const padLeft = parseFloat(style.paddingLeft) || 0;
    const padRight = parseFloat(style.paddingRight) || 0;
    const linkBox = link.getBoundingClientRect();
    const rootBox = root.getBoundingClientRect();
    setSpan({
      left: linkBox.left - rootBox.left + padLeft,
      width: Math.max(0, linkBox.width - padLeft - padRight),
    });
  }, []);

  // Đổi route thì mục đang mở đổi theo.
  useIsoLayoutEffect(() => {
    place();
  }, [pathname, place]);

  // Đổi kích thước (cửa sổ, nạp font) làm vị trí chữ lệch: đo lại về mục đang mở.
  useEffect(() => {
    const root = ref.current;
    if (!root || typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(() => place());
    observer.observe(root);
    return () => observer.disconnect();
  }, [place]);

  const navProps = {
    ref,
    onMouseOver: (event: MouseEvent<T>) =>
      place((event.target as Element).closest("a")),
    onMouseLeave: () => place(),
    onFocus: (event: FocusEvent<T>) =>
      place((event.target as Element).closest("a")),
    onBlur: () => place(),
  };

  const indicator = span ? (
    <span
      aria-hidden
      className="pointer-events-none absolute bottom-0 h-0.5 rounded-full bg-primary transition-[left,width] duration-200 ease-out motion-reduce:transition-none"
      style={{ left: span.left, width: span.width }}
    />
  ) : null;

  return { navProps, indicator };
}
