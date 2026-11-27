"use client";

import { useEffect, useRef, useState } from "react";

/** Chiều rộng hiện tại của một phần tử (px), theo dõi khi cửa sổ đổi cỡ. 0 cho tới lần đo đầu. */
export function useElementWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;
    setWidth(Math.floor(node.getBoundingClientRect().width));
    const observer = new ResizeObserver(([entry]) =>
      setWidth(Math.floor(entry.contentRect.width)),
    );
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  return { ref, width };
}
